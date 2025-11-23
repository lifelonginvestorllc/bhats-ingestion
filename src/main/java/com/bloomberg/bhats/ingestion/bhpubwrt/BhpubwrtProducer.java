package com.bloomberg.bhats.ingestion.bhpubwrt;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.bloomberg.bhats.ingestion.common.Payload;
import com.bloomberg.bhats.ingestion.common.PayloadStatus;
import com.bloomberg.bhats.ingestion.common.StatusPublisher;

@Component
public class BhpubwrtProducer implements StatusPublisher {

	private static final String REQUEST_TOPIC = "payload-topic";
	private static final String REPLY_TOPIC = "payload-status";

	@Autowired
	private KafkaTemplate<String, Payload> kafkaTemplate;

	@Autowired
	private org.springframework.kafka.core.KafkaTemplate<String, PayloadStatus> statusKafkaTemplate;

	@Autowired
	private PayloadSplitter payloadSplitter;

	// status templates from other clusters not used for sending; replies will
	// arrive via consumers

	private final ConcurrentMap<String, ClusterStatusAggregator> multiClusterStatus = new ConcurrentHashMap<>();

	public void send(Payload payload) {
		// Split payload into sub-payloads based on partition
		List<Payload> subPayloads = payloadSplitter.split(payload);

		// Send each sub-payload to Kafka with its partitionId
		for (Payload subPayload : subPayloads) {
			kafkaTemplate.send(REQUEST_TOPIC, subPayload.partitionId, null, subPayload);
		}

		// Initialize aggregator expecting 3 cluster replies for the original job
		// We need to track sub-payloads count for proper status aggregation
		multiClusterStatus.computeIfAbsent(payload.bhatsJobId,
			id -> new ClusterStatusAggregator(3, subPayloads.size()));
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
		// Extract original job ID from sub-payload ID (format: jobId-pN)
		String originalJobId = extractOriginalJobId(status.bhatsJobId);
		multiClusterStatus.computeIfAbsent(originalJobId,
			id -> new ClusterStatusAggregator(3, 1)).add(status);
	}

	public AggregatedPayloadStatus getAggregatedStatus(String bhatsJobId) {
		ClusterStatusAggregator agg = multiClusterStatus.get(bhatsJobId);
		return agg == null ? null : agg.toAggregated();
	}

	/**
	 * Extracts the original job ID from a sub-payload ID.
	 * Sub-payload IDs have format: originalJobId-pN where N is partition number.
	 *
	 * @param subPayloadId The sub-payload job ID
	 * @return The original job ID
	 */
	private String extractOriginalJobId(String subPayloadId) {
		if (subPayloadId == null) {
			return subPayloadId;
		}
		int partitionSuffixIndex = subPayloadId.lastIndexOf("-p");
		if (partitionSuffixIndex > 0) {
			return subPayloadId.substring(0, partitionSuffixIndex);
		}
		return subPayloadId;
	}
}
