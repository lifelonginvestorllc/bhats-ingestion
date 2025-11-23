package com.bloomberg.bhats.ingestion;

import com.bloomberg.bhats.ingestion.bhpubwrt.BhpubwrtProducer;
import com.bloomberg.bhats.ingestion.common.Payload;
import com.bloomberg.bhats.ingestion.common.PayloadStatus;
import com.bloomberg.bhats.ingestion.bhpubwrt.PayloadStatusStore;
import com.bloomberg.bhats.ingestion.bhwrtam.KafkaPayloadProcessor;
import com.bloomberg.bhats.ingestion.common.DataPayload;
import com.bloomberg.bhats.ingestion.common.Datapoint;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "payload.randomFailures=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class KafkaIntegrationTest {

    @Autowired
    private BhpubwrtProducer producer;

    @Autowired
    private KafkaPayloadProcessor payloadService;

    @Autowired
    private PayloadStatusStore statusStore;

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
    public void testKafkaPayloadProcessing() {
        statusStore.clear();
        int payloadCount = 3;
        int expectedBatchSize = 10; // keys: key0..key9
        List<String> bhatsJobIds = new ArrayList<>();
        for (int p = 1; p <= payloadCount; p++) {
            List<DataPayload> dataPayloads = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                DataPayload r = new DataPayload();
                r.tsid = "tsid" + (i % 10);
                Datapoint dp = new Datapoint();
                dp.column = "column" + (i % 3);
                dp.value = "datapoint" + i;
                r.datapoints = List.of(dp);
                dataPayloads.add(r);
            }
            String bhatsJobId = "partition-key-" + p;
            bhatsJobIds.add(bhatsJobId);
            Payload payload = new Payload(bhatsJobId, dataPayloads);
            producer.send(payload);
        }

        await().atMost(30, TimeUnit.SECONDS).until(() -> payloadService.getCompletedPayloads() == payloadCount);
        await().atMost(30, TimeUnit.SECONDS).until(() -> statusStore.size() == payloadCount);
        await().atMost(30, TimeUnit.SECONDS).until(() -> bhatsJobIds.stream().allMatch(id -> statusStore.get(id) != null));

        assertEquals(payloadCount, payloadService.getCompletedPayloads(), "All payloads should be completed");
        assertEquals(payloadCount, payloadService.getSuccessfulPayloadsCount(), "All payloads should be successful");
        bhatsJobIds.forEach(id -> {
            PayloadStatus s = statusStore.get(id);
            assertEquals(expectedBatchSize, s.batchCount, "Batch size should equal number of distinct keys");
            assertTrue(s.success, "Payload should be successful: " + id);
        });
        assertEquals(payloadCount, statusStore.size(), "All payload completion statuses should be published");
    }
}
