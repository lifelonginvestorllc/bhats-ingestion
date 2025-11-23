package com.bloomberg.bhats.ingestion.bhpubwrt;

import com.bloomberg.bhats.ingestion.common.DataPayload;
import com.bloomberg.bhats.ingestion.common.Payload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits a payload into multiple sub-payloads based on Kafka partition distribution.
 * Ensures that all DataPayloads with the same tsid are routed to the same partition
 * to preserve ordering.
 */
@Component
public class PayloadSplitter {

    @Value("${kafka.topic.payload.partitions:3}")
    private int numPartitions;

    /**
     * Splits a payload into multiple payloads, one per partition.
     * Each DataPayload is assigned to a partition based on the hash of its tsid.
     *
     * @param originalPayload The original payload to split
     * @return List of payloads, one per partition that has data
     */
    public List<Payload> split(Payload originalPayload) {
        if (originalPayload == null || originalPayload.dataPayloads == null || originalPayload.dataPayloads.isEmpty()) {
            return List.of();
        }

        // Group DataPayloads by partition
        Map<Integer, List<DataPayload>> partitionMap = new HashMap<>();

        for (DataPayload dataPayload : originalPayload.dataPayloads) {
            int partitionId = getPartitionId(dataPayload.tsid);
            partitionMap.computeIfAbsent(partitionId, k -> new ArrayList<>()).add(dataPayload);
        }

        // Create sub-payloads for each partition
        List<Payload> subPayloads = new ArrayList<>();
        for (Map.Entry<Integer, List<DataPayload>> entry : partitionMap.entrySet()) {
            int partitionId = entry.getKey();
            List<DataPayload> dataPayloads = entry.getValue();

            // Create sub-payload with partitionId suffix in bhatsJobId and set partitionId
            String subJobId = originalPayload.bhatsJobId + "-p" + partitionId;
            Payload subPayload = new Payload(subJobId, partitionId, dataPayloads);
            subPayloads.add(subPayload);
        }

        return subPayloads;
    }

    /**
     * Determines the partition for a given tsid using consistent hashing.
     *
     * @param tsid The time series identifier
     * @return The partition number (0 to numPartitions-1)
     */
    public int getPartitionId(String tsid) {
        if (tsid == null) {
            return 0;
        }
        // Use absolute value and modulo to ensure consistent partition assignment
        return Math.abs(tsid.hashCode() % numPartitions);
    }

    /**
     * Gets the number of partitions configured for the topic.
     *
     * @return The number of partitions
     */
    public int getNumPartitions() {
        return numPartitions;
    }

    /**
     * Sets the number of partitions (useful for testing).
     *
     * @param numPartitions The number of partitions
     */
    public void setNumPartitions(int numPartitions) {
        this.numPartitions = numPartitions;
    }
}

