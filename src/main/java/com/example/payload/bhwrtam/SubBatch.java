package com.example.payload.bhwrtam;

import com.example.payload.common.DataPayload;

import java.util.List;

public class SubBatch {
    public String bhatsJobId;
    public int index;
    public String key;
    public List<DataPayload> records;

    public SubBatch(String bhatsJobId, int index, String key, List<DataPayload> records) {
        this.bhatsJobId = bhatsJobId;
        this.index = index;
        this.key = key;
        this.records = records;
    }
}