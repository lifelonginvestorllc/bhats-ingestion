package com.bloomberg.bhats.ingestion.common;

import java.util.List;

public class Payload {
    public String bhatsJobId;
    public Integer partitionId;
    public Integer subBatchId;
    public List<DataPayload> dataPayloads;

    public Payload() {
    }

    public Payload(String bhatsJobId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.dataPayloads = dataPayloads;
    }

    public Payload(String bhatsJobId, Integer partitionId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.partitionId = partitionId;
        this.dataPayloads = dataPayloads;
    }

    public Payload(String bhatsJobId, Integer partitionId, Integer subBatchId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.partitionId = partitionId;
        this.subBatchId = subBatchId;
        this.dataPayloads = dataPayloads;
    }
}

