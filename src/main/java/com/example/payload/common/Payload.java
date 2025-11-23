package com.example.payload.common;

public class Payload {
    public String payloadId;
    public DataPayload[] data;

    public Payload() {
    }

    public Payload(String payloadId, DataPayload[] data) {
        this.payloadId = payloadId;
        this.data = data;
    }
}

