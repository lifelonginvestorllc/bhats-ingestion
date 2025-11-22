package com.example.payload;

public class PayloadCompletionStatus {
    public String payloadId;
    public boolean success;
    public int batchCount;
    public long completedAt;

    public PayloadCompletionStatus() {}

    public PayloadCompletionStatus(String payloadId, boolean success, int batchCount) {
        this.payloadId = payloadId;
        this.success = success;
        this.batchCount = batchCount;
        this.completedAt = System.currentTimeMillis();
    }
}

