package com.bloomberg.bhats.ingestion;

import com.bloomberg.bhats.ingestion.bhwrtam.KafkaPayloadProcessor;
import com.bloomberg.bhats.ingestion.common.DataPayload;
import com.bloomberg.bhats.ingestion.common.Datapoint;
import com.bloomberg.bhats.ingestion.common.Payload;
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
        String bhatsJobId = "shutdown-test";
        List<DataPayload> dataPayloads = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            DataPayload r = new DataPayload();
            r.tsid = "tsid" + (i % 5);
            Datapoint dp = new Datapoint();
            dp.column = "column" + (i % 3);
            dp.value = "datapoint" + i;
            r.datapoints = List.of(dp);
            dataPayloads.add(r);
        }
        Payload payload = new Payload(bhatsJobId, dataPayloads);
        payloadService.submitKafkaPayload(payload);

        await().atMost(15, TimeUnit.SECONDS).until(() -> payloadService.getCompletedPayloads() >= 1);
        int completedBeforeShutdown = payloadService.getCompletedPayloads();
        payloadService.shutdown();
        Thread.sleep(1000); // allow any in-flight tasks to finish
        int completedAfterShutdown = payloadService.getCompletedPayloads();
        assertEquals(completedBeforeShutdown, completedAfterShutdown, "No new payloads should complete after shutdown");
    }
}
