package com.example.payload;

import com.example.payload.bhpubwrt.BhpubwrtProducer;
import com.example.payload.common.PayloadStatus;
import com.example.payload.bhpubwrt.StatusStore;
import com.example.payload.bhwrtam.KafkaPayloadProcessor;
import com.example.payload.common.DataPayload;
import com.example.payload.common.Datapoint;
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
import java.util.Arrays;
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
    private StatusStore statusStore;

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
        List<String> payloadIds = new ArrayList<>();
        for (int p = 1; p <= payloadCount; p++) {
            List<DataPayload> records = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                DataPayload r = new DataPayload();
                r.tsid = "tsid" + (i % 10);
                Datapoint dp = new Datapoint();
                dp.value = "datapoint" + i;
                r.datapoints = List.of(dp);
                records.add(r);
            }
            String payloadId = "partition-key-" + p;
            payloadIds.add(payloadId);
            producer.send(payloadId, records);
        }

        await().atMost(30, TimeUnit.SECONDS).until(() -> payloadService.getCompletedPayloads() == payloadCount);
        await().atMost(30, TimeUnit.SECONDS).until(() -> statusStore.size() == payloadCount);
        await().atMost(30, TimeUnit.SECONDS).until(() -> payloadIds.stream().allMatch(id -> statusStore.get(id) != null));

        assertEquals(payloadCount, payloadService.getCompletedPayloads(), "All payloads should be completed");
        assertEquals(payloadCount, payloadService.getSuccessfulPayloadsCount(), "All payloads should be successful");
        payloadIds.forEach(id -> {
            PayloadStatus s = statusStore.get(id);
            assertEquals(expectedBatchSize, s.batchCount, "Batch size should equal number of distinct keys");
            assertTrue(s.success, "Payload should be successful: " + id);
        });
        assertEquals(payloadCount, statusStore.size(), "All payload completion statuses should be published");
    }
}
