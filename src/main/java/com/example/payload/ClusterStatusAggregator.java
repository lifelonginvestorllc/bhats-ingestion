package com.example.payload;

class ClusterStatusAggregator {
    private final int expected;
    private final java.util.List<PayloadStatus> received = new java.util.concurrent.CopyOnWriteArrayList<>();

    ClusterStatusAggregator(int expected) {
        this.expected = expected;
    }

    void add(PayloadStatus s) {
        received.add(s);
    }

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
