package com.example.payload.common;

public class Payload {
    public String bhatsJobId;
    public DataPayload[] data;

    public Payload() {
    }

    public Payload(String bhatsJobId, DataPayload[] data) {
        this.bhatsJobId = bhatsJobId;
        this.data = data;
    }
}

