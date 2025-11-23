package com.example.payload.bhpubwrt;

class ClusterStatusAggregator {
    private final int expected;
    private final java.util.Map<String, PayloadStatus> byCluster = new java.util.concurrent.ConcurrentHashMap<>();

    ClusterStatusAggregator(int expected) {
        this.expected = expected;
    }

    void add(PayloadStatus s) {
        // replace existing to reflect latest status (e.g., failure after provisional success)
        byCluster.put(s.clusterId, s);
    }

    AggregatedPayloadStatus toAggregated() {
        java.util.Collection<PayloadStatus> received = byCluster.values();
        boolean allReceived = received.size() >= expected;
        boolean allSuccess = allReceived && received.stream().allMatch(r -> r.success);
        boolean anySuccess = received.stream().anyMatch(r -> r.success);
        int batchCount = received.stream().map(r -> r.batchCount).max(Integer::compareTo).orElse(0);
        java.util.Set<String> clusters = new java.util.HashSet<>(byCluster.keySet());
        return new AggregatedPayloadStatus(allReceived, allSuccess, anySuccess, batchCount, received.size(), clusters);
    }
}
