package com.example.payload;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class KafkaPayloadProducer {

    private static final String REQUEST_TOPIC = "payload-topic";
    private static final String REPLY_TOPIC = "payload-status";

    @Autowired
    private KafkaTemplate<String, TSValues[]> kafkaTemplate;

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, PayloadCompletionStatus> statusKafkaTemplate;

    // status templates from other clusters not used for sending; replies will arrive via consumers

    private final ConcurrentMap<String, ClusterStatusAggregator> multiClusterStatus = new ConcurrentHashMap<>();

    public void send(String key, List<TSValues> records) {
        kafkaTemplate.send(REQUEST_TOPIC, key, records.toArray(new TSValues[0]));
        // initialize aggregator expecting 3 cluster replies (configurable later)
        multiClusterStatus.computeIfAbsent(key, id -> new ClusterStatusAggregator(3));
    }

    public void sendStatus(PayloadCompletionStatus status) {
        statusKafkaTemplate.send(REPLY_TOPIC, status.payloadId, status);
    }

    // Called by status consumers when each cluster replies
    void onStatus(PayloadCompletionStatus status) {
        multiClusterStatus.computeIfAbsent(status.payloadId, id -> new ClusterStatusAggregator(3)).add(status);
    }

    public AggregatedPayloadStatus getAggregatedStatus(String payloadId) {
        ClusterStatusAggregator agg = multiClusterStatus.get(payloadId);
        return agg == null ? null : agg.toAggregated();
    }
}

class ClusterStatusAggregator {
    private final int expected;
    private final java.util.List<PayloadCompletionStatus> received = new java.util.concurrent.CopyOnWriteArrayList<>();
    ClusterStatusAggregator(int expected) { this.expected = expected; }
    void add(PayloadCompletionStatus s) { received.add(s); }
    AggregatedPayloadStatus toAggregated() {
        boolean allReceived = received.size() >= expected;
        boolean allSuccess = allReceived && received.stream().allMatch(r -> r.success);
        boolean anySuccess = received.stream().anyMatch(r -> r.success);
        int batchCount = received.stream().map(r -> r.batchCount).max(Integer::compareTo).orElse(0);
        java.util.Set<String> clusters = new java.util.HashSet<>();
        received.forEach(r -> clusters.add(r.clusterId));
        return new AggregatedPayloadStatus(allReceived, allSuccess, anySuccess, batchCount, received.size(), clusters);
    }
}

class AggregatedPayloadStatus {
    public final boolean allClustersReported;
    public final boolean allSuccessful;
    public final boolean atLeastOneSuccess;
    public final int maxBatchCount;
    public final int repliesReceived;
    public final java.util.Set<String> clusterIds;
    AggregatedPayloadStatus(boolean allClustersReported, boolean allSuccessful, boolean atLeastOneSuccess, int maxBatchCount, int repliesReceived, java.util.Set<String> clusterIds) {
        this.allClustersReported = allClustersReported;
        this.allSuccessful = allSuccessful;
        this.atLeastOneSuccess = atLeastOneSuccess;
        this.maxBatchCount = maxBatchCount;
        this.repliesReceived = repliesReceived;
        this.clusterIds = clusterIds;
    }
}