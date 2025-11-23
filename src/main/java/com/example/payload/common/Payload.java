package com.example.payload.common;

import java.util.List;

public class Payload {
    public String bhatsJobId;
    public List<DataPayload> dataPayloads;

    public Payload() {
    }

    public Payload(String bhatsJobId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.dataPayloads = dataPayloads;
    }
}

