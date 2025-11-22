package com.example.payload;

import java.util.List;

public class PayloadBatch {
    public String payloadId;
    public int index;
    public String key;
    public List<Record> records;

    public PayloadBatch(String payloadId, int index, String key, List<Record> records) {
        this.payloadId = payloadId;
        this.index = index;
        this.key = key;
        this.records = records;
    }
}