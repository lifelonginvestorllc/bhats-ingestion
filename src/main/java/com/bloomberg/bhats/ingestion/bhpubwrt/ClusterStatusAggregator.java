package com.bloomberg.bhats.ingestion.bhpubwrt;

import com.bloomberg.bhats.ingestion.common.PayloadStatus;

class ClusterStatusAggregator {
    private final int expectedClusters;
    private final int expectedSubPayloads;
    private final java.util.List<PayloadStatus> received = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.Set<Integer> receivedBatchIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    ClusterStatusAggregator(int expectedClusters) {
        this(expectedClusters, 1);
    }

    ClusterStatusAggregator(int expectedClusters, int expectedSubPayloads) {
        this.expectedClusters = expectedClusters;
        this.expectedSubPayloads = expectedSubPayloads;
    }

    void add(PayloadStatus s) {
        received.add(s);
        if (s.batchId != null) {
            receivedBatchIds.add(s.batchId);
        }
    }

    AggregatedPayloadStatus toAggregated() {
        int totalExpected = expectedClusters * expectedSubPayloads;
        boolean allReceived = received.size() >= totalExpected;
        boolean allPartitionsReceived = receivedBatchIds.size() >= expectedSubPayloads;
        boolean allSuccess = allReceived && received.stream().allMatch(r -> r.success);
        boolean anySuccess = received.stream().anyMatch(r -> r.success);

        // Sum batch counts across all sub-payloads
        int totalBatchCount = received.stream().mapToInt(r -> r.batchCount).sum();

        java.util.Set<String> clusters = new java.util.HashSet<>();
        received.forEach(r -> clusters.add(r.clusterId));

        System.out.printf("Status aggregation: received=%d/%d, partitions=%d/%d, success=%b%n",
            received.size(), totalExpected, receivedBatchIds.size(), expectedSubPayloads, allSuccess);

        return new AggregatedPayloadStatus(allReceived && allPartitionsReceived, allSuccess, anySuccess,
            totalBatchCount, received.size(), clusters);
    }
}
