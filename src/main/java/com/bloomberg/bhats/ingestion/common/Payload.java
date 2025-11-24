package com.bloomberg.bhats.ingestion.common;

import java.util.List;

public class Payload {
    public String bhatsJobId;
    public Integer batchId;
    public Integer subBatchId;
    public List<DataPayload> dataPayloads;

    public Payload() {
    }

    public Payload(String bhatsJobId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.dataPayloads = dataPayloads;
    }

    public Payload(String bhatsJobId, Integer batchId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.batchId = batchId;
        this.dataPayloads = dataPayloads;
    }

    public Payload(String bhatsJobId, Integer batchId, Integer subBatchId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.batchId = batchId;
        this.subBatchId = subBatchId;
        this.dataPayloads = dataPayloads;
    }

    public String getUniqueBatchId() {
        return bhatsJobId + "-batch" + batchId;
    }

    public String getUniqueSubBatchId() {
        return getUniqueBatchId() + "-subBatch" + subBatchId;
    }
}

