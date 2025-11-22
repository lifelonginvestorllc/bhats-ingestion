package com.example.payload;

import java.util.List;

public class PayloadBatch {
    public String payloadId;
    public int index;
    public String key;
    public List<TSValues> records;

    public PayloadBatch(String payloadId, int index, String key, List<TSValues> records) {
        this.payloadId = payloadId;
        this.index = index;
        this.key = key;
        this.records = records;
    }
}