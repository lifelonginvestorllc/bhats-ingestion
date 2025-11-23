package com.bloomberg.bhats.ingestion.bhwrtam;

import com.bloomberg.bhats.ingestion.common.DataPayload;

import java.util.List;

public class BatchPayload {
    public String bhatsJobId;
    public Integer partitionId;
    public Integer subBatchId;
    public List<DataPayload> dataPayloads;

    public BatchPayload(String bhatsJobId, Integer partitionId, Integer subBatchId, List<DataPayload> dataPayloads) {
        this.bhatsJobId = bhatsJobId;
        this.partitionId = partitionId;
        this.subBatchId = subBatchId;
        this.dataPayloads = dataPayloads;
    }
}