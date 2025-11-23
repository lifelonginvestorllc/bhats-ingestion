package com.example.payload.bhwrtam;

import com.example.payload.common.DataPayload;

import java.util.List;

public class SubBatch {
    public String bhatsJobId;
    public int index;
    public String key;
    public List<DataPayload> dataPayloads;

    public SubBatch(String bhatsJobId, int index, String key, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.index = index;
        this.key = key;
        this.dataPayloads = dataPayloads;
    }
}