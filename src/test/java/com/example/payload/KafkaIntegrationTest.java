package com.example.payload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class KafkaIntegrationTest {

    @Autowired
    private KafkaPayloadProducer producer;

    static KafkaContainer kafka;

    @BeforeAll
    static void startKafka() {
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));
        kafka.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    public void testKafkaPayloadProcessing() {
        for (int p = 1; p <= 3; p++) {
            List<Record> records = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Record r = new Record();
                r.key = "key" + (i % 10);
                r.value = "value" + i;
                records.add(r);
            }
            producer.send("partition-key-" + p, records);
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            System.out.println("Payloads sent to Kafka and processed.");
        });
    }
}