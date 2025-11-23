package com.example.payload.common;

/**
 * Represents the completion status of a payload from a single cluster/consumer group.
 * This is a shared model used by both bhpubwrt (producer/aggregator) and bhwrtam (consumer/processor).
 */
public class PayloadStatus {
    public String payloadId;
    public boolean success;
    public int batchCount;
    public long completedAt;
    public String clusterId; // identifies source cluster

    public PayloadStatus() {}

    public PayloadStatus(String payloadId, boolean success, int batchCount, String clusterId) {
        this.payloadId = payloadId;
        this.success = success;
        this.batchCount = batchCount;
        this.completedAt = System.currentTimeMillis();
        this.clusterId = clusterId;
    }
}

