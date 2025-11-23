package com.example.payload.bhpubwrt;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.payload.TSValues;

@Component
public class BhpubwrtProducer {

	private static final String REQUEST_TOPIC = "payload-topic";
	private static final String REPLY_TOPIC = "payload-status";

	@Autowired
	private KafkaTemplate<String, TSValues[]> kafkaTemplate;

	@Autowired
	private org.springframework.kafka.core.KafkaTemplate<String, PayloadStatus> statusKafkaTemplate;

	// status templates from other clusters not used for sending; replies will
	// arrive via consumers

	private final ConcurrentMap<String, ClusterStatusAggregator> multiClusterStatus = new ConcurrentHashMap<>();

	public void send(String key, List<TSValues> records) {
		kafkaTemplate.send(REQUEST_TOPIC, key, records.toArray(new TSValues[0]));
		// initialize aggregator expecting 3 cluster replies (configurable later)
		multiClusterStatus.computeIfAbsent(key, id -> new ClusterStatusAggregator(3));
	}

	public void sendStatus(PayloadStatus status) {
		statusKafkaTemplate.send(REPLY_TOPIC, status.payloadId, status);
	}

	// Called by status consumers when each cluster replies
	public void onStatus(PayloadStatus status) {
		multiClusterStatus.computeIfAbsent(status.payloadId, id -> new ClusterStatusAggregator(3)).add(status);
	}

	public AggregatedPayloadStatus getAggregatedStatus(String payloadId) {
		ClusterStatusAggregator agg = multiClusterStatus.get(payloadId);
		return agg == null ? null : agg.toAggregated();
	}
}
