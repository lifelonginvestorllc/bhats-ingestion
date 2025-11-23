package com.example.payload.bhpubwrt;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.payload.common.DataPayload;
import com.example.payload.common.Payload;
import com.example.payload.common.PayloadStatus;
import com.example.payload.common.StatusPublisher;

@Component
public class BhpubwrtProducer implements StatusPublisher {

	private static final String REQUEST_TOPIC = "payload-topic";
	private static final String REPLY_TOPIC = "payload-status";

	@Autowired
	private KafkaTemplate<String, Payload> kafkaTemplate;

	@Autowired
	private org.springframework.kafka.core.KafkaTemplate<String, PayloadStatus> statusKafkaTemplate;

	// status templates from other clusters not used for sending; replies will
	// arrive via consumers

	private final ConcurrentMap<String, ClusterStatusAggregator> multiClusterStatus = new ConcurrentHashMap<>();

	public void send(String key, List<DataPayload> records) {
		Payload payload = new Payload(key, records);
		kafkaTemplate.send(REQUEST_TOPIC, key, payload);
		// initialize aggregator expecting 3 cluster replies (configurable later)
		multiClusterStatus.computeIfAbsent(key, id -> new ClusterStatusAggregator(3));
	}

	@Override
	public void publishStatus(PayloadStatus status) {
		statusKafkaTemplate.send(REPLY_TOPIC, status.bhatsJobId, status);
	}

	// Deprecated: use publishStatus instead
	public void sendStatus(PayloadStatus status) {
		publishStatus(status);
	}

	// Called by status consumers when each cluster replies
	public void onStatus(PayloadStatus status) {
		multiClusterStatus.computeIfAbsent(status.bhatsJobId, id -> new ClusterStatusAggregator(3)).add(status);
	}

	public AggregatedPayloadStatus getAggregatedStatus(String bhatsJobId) {
		ClusterStatusAggregator agg = multiClusterStatus.get(bhatsJobId);
		return agg == null ? null : agg.toAggregated();
	}
}
