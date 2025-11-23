package com.bloomberg.bhats.ingestion.bhwrtam;

import com.bloomberg.bhats.ingestion.common.DataPayload;

import java.util.List;

public class BatchPayload {
    public String bhatsJobId;
    public Integer batchId;
    public List<DataPayload> dataPayloads;

    public BatchPayload(String bhatsJobId, Integer batchId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.batchId = batchId;
        this.dataPayloads = dataPayloads;
    }
}