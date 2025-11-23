package com.bloomberg.bhats.ingestion.bhwrtam;

import com.bloomberg.bhats.ingestion.common.DataPayload;

import java.util.List;

public class BatchPayload {
    public String bhatsJobId;
    public int index;
    public String key;
    public List<DataPayload> dataPayloads;

    public BatchPayload(String bhatsJobId, int index, String key, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.index = index;
        this.key = key;
        this.dataPayloads = dataPayloads;
    }
}