package com.example.payload.common;

import java.util.List;

public class Payload {
    public String bhatsJobId;
    public List<DataPayload> data;

    public Payload() {
    }

    public Payload(String bhatsJobId, List<DataPayload> data) {
        this.bhatsJobId = bhatsJobId;
        this.data = data;
    }
}

