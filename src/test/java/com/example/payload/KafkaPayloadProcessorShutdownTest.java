package com.example.payload;

import com.example.payload.bhwrtam.KafkaPayloadProcessor;
import com.example.payload.common.TSValues;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "payload.randomFailures=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class KafkaPayloadProcessorShutdownTest {

    @Autowired
    private KafkaPayloadProcessor payloadService;

    static KafkaContainer kafka;

    @BeforeAll
    static void startKafka() {
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
        kafka.start();
    }

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @AfterAll
    static void stopKafka() {
        if (kafka != null) {
            kafka.stop();
        }
    }

    @Test
    void testNoProcessingAfterShutdown() throws InterruptedException {
        String payloadId = "shutdown-test";
        List<TSValues> records = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            TSValues r = new TSValues();
            r.key = "key" + (i % 5);
            r.value = "value" + i;
            records.add(r);
        }
        payloadService.submitLargePayload(payloadId, records);

        await().atMost(15, TimeUnit.SECONDS).until(() -> payloadService.getCompletedPayloads() >= 1);
        int completedBeforeShutdown = payloadService.getCompletedPayloads();
        payloadService.shutdown();
        Thread.sleep(1000); // allow any in-flight tasks to finish
        int completedAfterShutdown = payloadService.getCompletedPayloads();
        assertEquals(completedBeforeShutdown, completedAfterShutdown, "No new payloads should complete after shutdown");
    }
}
