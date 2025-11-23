package com.example.payload.bhwrtam;

import com.example.payload.common.TSValues;

import java.util.List;

public class SubBatch {
    public String payloadId;
    public int index;
    public String key;
    public List<TSValues> records;

    public SubBatch(String payloadId, int index, String key, List<TSValues> records) {
        this.payloadId = payloadId;
        this.index = index;
        this.key = key;
        this.records = records;
    }
}