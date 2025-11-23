package com.example.payload;

import com.example.payload.bhpubwrt.AggregatedPayloadStatus;
import com.example.payload.bhpubwrt.BhpubwrtProducer;
import com.example.payload.bhwrtam.KafkaPayloadProcessor;
import com.example.payload.common.DataPayload;
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
        String payloadId = "multi-cluster-1";
        List<DataPayload> records = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            DataPayload r = new DataPayload();
            r.key = "key" + (i % 10);
            r.value = "value" + i;
            records.add(r);
        }
        producer.send(payloadId, records);

        await().atMost(30, TimeUnit.SECONDS).until(() -> payloadService.getCompletedPayloads() >= 1);
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            AggregatedPayloadStatus agg = producer.getAggregatedStatus(payloadId);
            return agg != null && agg.repliesReceived >= 3;
        });

        AggregatedPayloadStatus agg = producer.getAggregatedStatus(payloadId);
        assertNotNull(agg, "Aggregated status should be available");
        assertTrue(agg.allClustersReported, "All clusters should have reported");
        assertTrue(agg.allSuccessful, "All cluster statuses should be successful");
        assertTrue(agg.repliesReceived >= 3, "At least three cluster replies expected");
        assertEquals(10, agg.maxBatchCount, "Batch count should reflect number of distinct keys");
        assertEquals(3, agg.clusterIds.size(), "Should have 3 distinct clusterIds");
    }
}
