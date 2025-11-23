package com.bloomberg.bhats.ingestion;

import com.bloomberg.bhats.ingestion.bhpubwrt.AggregatedPayloadStatus;
import com.bloomberg.bhats.ingestion.bhpubwrt.BhpubwrtProducer;
import com.bloomberg.bhats.ingestion.bhwrtam.KafkaPayloadProcessor;
import com.bloomberg.bhats.ingestion.common.DataPayload;
import com.bloomberg.bhats.ingestion.common.Datapoint;
import com.bloomberg.bhats.ingestion.common.Payload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "payload.randomFailures=false",
        // Reuse same container for cluster2 and cluster3 to simulate multi-cluster (simplified)
        "spring.kafka.cluster2.bootstrap-servers=${spring.kafka.bootstrap-servers}",
        "spring.kafka.cluster3.bootstrap-servers=${spring.kafka.bootstrap-servers}"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MultiClusterKafkaIntegrationTest {

    @Autowired
    private BhpubwrtProducer producer;

    @Autowired
    private KafkaPayloadProcessor payloadService;

    static KafkaContainer kafka;

    @BeforeAll
    static void startKafka() {
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
        kafka.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @AfterAll
    static void stopKafka() {
        if (kafka != null) {
            kafka.stop();
        }
    }

    @Test
    void testAggregatedMultiClusterStatus() {
        String bhatsJobId = "multi-cluster-1";
        List<DataPayload> dataPayloads = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            DataPayload r = new DataPayload();
            r.tsid = "tsid" + (i % 10);
            Datapoint dp = new Datapoint();
            dp.periodDate = new Date(System.currentTimeMillis() + (i % 3) * 86400000L);
            dp.priceValue = 100.0 + i;
            r.datapoints = List.of(dp);
            dataPayloads.add(r);
        }
        Payload payload = new Payload(bhatsJobId, dataPayloads);
        producer.send(payload);

        // Wait for all sub-payloads from all clusters to complete
        // With 3 partitions and 3 clusters, we expect 9 replies (3×3)
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            AggregatedPayloadStatus agg = producer.getAggregatedStatus(bhatsJobId);
            return agg != null && agg.repliesReceived >= 9;
        });

        AggregatedPayloadStatus agg = producer.getAggregatedStatus(bhatsJobId);
        assertNotNull(agg, "Aggregated status should be available");
        assertTrue(agg.allClustersReported, "All clusters should have reported");
        assertTrue(agg.allSuccessful, "All cluster statuses should be successful");
        assertTrue(agg.repliesReceived >= 9, "Should receive 9 replies (3 clusters × 3 partitions)");
        // maxBatchCount now contains the sum of all batch counts across all clusters and partitions
        // With 10 distinct tsids and 3 clusters, total is 30 (10 × 3)
        assertEquals(30, agg.maxBatchCount, "Total batch count should be 30 (10 tsids × 3 clusters)");
        assertEquals(3, agg.clusterIds.size(), "Should have 3 distinct clusterIds");
    }
}
