package com.example.payload;

public class PayloadStatus {
    public String payloadId;
    public boolean success;
    public int batchCount;
    public long completedAt;
    public String clusterId; // new field identifying source cluster

    public PayloadStatus() {}

    public PayloadStatus(String payloadId, boolean success, int batchCount, String clusterId) {
        this.payloadId = payloadId;
        this.success = success;
        this.batchCount = batchCount;
        this.completedAt = System.currentTimeMillis();
        this.clusterId = clusterId;
    }
}
