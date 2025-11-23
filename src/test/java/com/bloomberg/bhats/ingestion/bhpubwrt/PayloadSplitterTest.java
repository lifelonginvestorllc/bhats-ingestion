package com.bloomberg.bhats.ingestion.bhpubwrt;

import com.bloomberg.bhats.ingestion.common.DataPayload;
import com.bloomberg.bhats.ingestion.common.Datapoint;
import com.bloomberg.bhats.ingestion.common.Payload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class PayloadSplitterTest {

    private PayloadSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new PayloadSplitter();
        splitter.setNumPartitions(3);
    }

    @Test
    void testSplitEmptyPayload() {
        Payload payload = new Payload("job-1", List.of());
        List<Payload> result = splitter.split(payload);
        assertTrue(result.isEmpty(), "Empty payload should result in empty list");
    }

    @Test
    void testSplitNullPayload() {
        List<Payload> result = splitter.split(null);
        assertTrue(result.isEmpty(), "Null payload should result in empty list");
    }

    @Test
    void testSplitWithMultiplePartitions() {
        List<DataPayload> dataPayloads = new ArrayList<>();

        // Create data that will distribute across partitions
        for (int i = 0; i < 30; i++) {
            DataPayload dp = new DataPayload();
            dp.tsid = "tsid" + i;
            Datapoint datapoint = new Datapoint();
            datapoint.column = "column1";
            datapoint.value = "value" + i;
            dp.datapoints = List.of(datapoint);
            dataPayloads.add(dp);
        }

        Payload payload = new Payload("job-1", dataPayloads);
        List<Payload> subPayloads = splitter.split(payload);

        // Should have between 1 and 3 sub-payloads depending on distribution
        assertFalse(subPayloads.isEmpty(), "Should have at least one sub-payload");
        assertTrue(subPayloads.size() <= 3, "Should have at most 3 sub-payloads");

        // Verify total count matches
        int totalDataPayloads = subPayloads.stream()
            .mapToInt(p -> p.dataPayloads.size())
            .sum();
        assertEquals(30, totalDataPayloads, "Total DataPayloads should match original");

        // Verify each sub-payload has correct job ID format
        for (Payload subPayload : subPayloads) {
            assertTrue(subPayload.bhatsJobId.startsWith("job-1-p"),
                "Sub-payload ID should start with original ID plus partition suffix");
        }
    }

    @Test
    void testConsistentPartitioning() {
        String tsid = "test-tsid-123";

        // Same tsid should always map to same partition
        int partitionId1 = splitter.getPartitionId(tsid);
        int partitionId2 = splitter.getPartitionId(tsid);

        assertEquals(partitionId1, partitionId2, "Same tsid should always map to same partition");
        assertTrue(partitionId1 >= 0 && partitionId1 < 3, "Partition should be in valid range");
    }

    @Test
    void testPartitionIdIsSet() {
        List<DataPayload> dataPayloads = new ArrayList<>();

        // Create data that will distribute across partitions
        for (int i = 0; i < 30; i++) {
            DataPayload dp = new DataPayload();
            dp.tsid = "tsid" + i;
            Datapoint datapoint = new Datapoint();
            datapoint.column = "column1";
            datapoint.value = "value" + i;
            dp.datapoints = List.of(datapoint);
            dataPayloads.add(dp);
        }

        Payload payload = new Payload("job-1", dataPayloads);
        List<Payload> subPayloads = splitter.split(payload);

        // Verify each sub-payload has partitionId set correctly
        for (Payload subPayload : subPayloads) {
            assertNotNull(subPayload.partitionId, "Sub-payload should have partitionId set");
            assertTrue(subPayload.partitionId >= 0 && subPayload.partitionId < 3,
                "PartitionId should be in valid range");

            // Verify the job ID suffix matches the partition ID
            assertTrue(subPayload.bhatsJobId.endsWith("-p" + subPayload.partitionId),
                "Job ID suffix should match partitionId");

            // Verify all tsids in this sub-payload map to the same partition
            for (DataPayload dp : subPayload.dataPayloads) {
                assertEquals(subPayload.partitionId, splitter.getPartitionId(dp.tsid),
                    "All tsids in sub-payload should map to the sub-payload's partitionId");
            }
        }
    }

    @Test
    void testAllTsidsInSameSubPayload() {
        List<DataPayload> dataPayloads = new ArrayList<>();

        // Create multiple DataPayloads with the same tsid
        for (int i = 0; i < 5; i++) {
            DataPayload dp = new DataPayload();
            dp.tsid = "same-tsid";
            Datapoint datapoint = new Datapoint();
            datapoint.column = "column" + i;
            datapoint.value = "value" + i;
            dp.datapoints = List.of(datapoint);
            dataPayloads.add(dp);
        }

        Payload payload = new Payload("job-1", dataPayloads);
        List<Payload> subPayloads = splitter.split(payload);

        // All should be in one sub-payload since they have the same tsid
        assertEquals(1, subPayloads.size(), "All same tsids should be in one sub-payload");
        assertEquals(5, subPayloads.get(0).dataPayloads.size(), "Sub-payload should contain all DataPayloads");
    }

    @Test
    void testPartitionDistribution() {
        List<DataPayload> dataPayloads = new ArrayList<>();

        // Create data that we know will distribute across partitions
        for (int i = 0; i < 100; i++) {
            DataPayload dp = new DataPayload();
            dp.tsid = "tsid" + (i % 10); // 10 different tsids
            Datapoint datapoint = new Datapoint();
            datapoint.column = "column1";
            datapoint.value = "value" + i;
            dp.datapoints = List.of(datapoint);
            dataPayloads.add(dp);
        }

        Payload payload = new Payload("job-1", dataPayloads);
        List<Payload> subPayloads = splitter.split(payload);

        // Verify that tsids are grouped correctly
        for (Payload subPayload : subPayloads) {
            // All tsids in a sub-payload should map to the same partition
            Map<Integer, Long> partitionCounts = subPayload.dataPayloads.stream()
                .collect(Collectors.groupingBy(
                    dp -> splitter.getPartitionId(dp.tsid),
                    Collectors.counting()
                ));

            assertEquals(1, partitionCounts.size(),
                "All DataPayloads in a sub-payload should map to the same partition");
        }
    }

    @Test
    void testNullTsidHandling() {
        List<DataPayload> dataPayloads = new ArrayList<>();

        DataPayload dp = new DataPayload();
        dp.tsid = null;
        Datapoint datapoint = new Datapoint();
        datapoint.column = "column1";
        datapoint.value = "value1";
        dp.datapoints = List.of(datapoint);
        dataPayloads.add(dp);

        Payload payload = new Payload("job-1", dataPayloads);
        List<Payload> subPayloads = splitter.split(payload);

        assertFalse(subPayloads.isEmpty(), "Should handle null tsid gracefully");
        assertEquals(1, subPayloads.size(), "Null tsid should be assigned to partition 0");
    }
}

