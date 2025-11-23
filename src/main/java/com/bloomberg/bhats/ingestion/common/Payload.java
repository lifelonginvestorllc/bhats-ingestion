package com.bloomberg.bhats.ingestion.common;

import java.util.List;

public class Payload {
    public String bhatsJobId;
    public Integer partitionId;
    public Integer batchId;
    public List<DataPayload> dataPayloads;

    public Payload() {
    }

    public Payload(String bhatsJobId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.dataPayloads = dataPayloads;
    }
}

