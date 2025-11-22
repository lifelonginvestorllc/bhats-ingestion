package com.example.payload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
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
public class KafkaIntegrationTest {

    @Autowired
    private KafkaPayloadProducer producer;

    @Autowired
    private PayloadService payloadService;

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
        int payloadCount = 3;
        for (int p = 1; p <= payloadCount; p++) {
            List<Record> records = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Record r = new Record();
                r.key = "key" + (i % 10);
                r.value = "value" + i;
                records.add(r);
            }
            producer.send("partition-key-" + p, records);
        }

        await().atMost(30, TimeUnit.SECONDS).until(() -> payloadService.getCompletedPayloads() == payloadCount);
        assertEquals(payloadCount, payloadService.getCompletedPayloads(), "All payloads should be completed");
        assertEquals(payloadCount, payloadService.getSuccessfulPayloadsCount(), "All payloads should be successful");
        assertTrue(payloadService.getSuccessfulPayloadIds().size() == payloadCount, "Successful payload IDs should equal payload count");
    }
}
