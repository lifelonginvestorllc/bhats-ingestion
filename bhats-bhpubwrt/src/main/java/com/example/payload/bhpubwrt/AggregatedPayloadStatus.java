package com.example.payload.bhpubwrt;

public class AggregatedPayloadStatus {
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
