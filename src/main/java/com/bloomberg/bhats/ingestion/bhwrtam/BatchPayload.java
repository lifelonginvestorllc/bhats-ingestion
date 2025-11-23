package com.bloomberg.bhats.ingestion.bhwrtam;

import com.bloomberg.bhats.ingestion.common.DataPayload;

import java.util.List;

public class BatchPayload {
    public String bhatsJobId;
    public Integer queueId;
    public List<DataPayload> dataPayloads;

    public BatchPayload(String bhatsJobId, Integer queueId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.queueId = queueId;
        this.dataPayloads = dataPayloads;
    }
}