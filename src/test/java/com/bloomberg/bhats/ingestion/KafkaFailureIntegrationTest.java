package com.bloomberg.bhats.ingestion;

import com.bloomberg.bhats.ingestion.bhpubwrt.BhpubwrtProducer;
import com.bloomberg.bhats.ingestion.common.Datapoint;
import com.bloomberg.bhats.ingestion.common.Payload;
import com.bloomberg.bhats.ingestion.common.PayloadStatus;
import com.bloomberg.bhats.ingestion.bhpubwrt.PayloadStatusStore;
import com.bloomberg.bhats.ingestion.bhwrtam.KafkaPayloadProcessor;
import com.bloomberg.bhats.ingestion.common.DataPayload;
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
        "payload.failKey=tsid3"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class KafkaFailureIntegrationTest {

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
    public void testForcedFailurePayload() {
        statusStore.clear();
        String bhatsJobId = "failure-payload";
        List<DataPayload> dataPayloads = new ArrayList<>();
        // Ensure "tsid3" appears so forced failure triggers at least one batch
        for (int i = 0; i < 50; i++) {
            DataPayload r = new DataPayload();
            r.tsid = "tsid" + (i % 10); // includes "tsid3"
            Datapoint dp = new Datapoint();
            dp.periodDate = new Date(System.currentTimeMillis() + (i % 3) * 86400000L);
            dp.priceValue = 100.0 + i;
            r.datapoints = List.of(dp);
            dataPayloads.add(r);
        }
        Payload payload = new Payload(bhatsJobId, dataPayloads);
        producer.send(payload);

        // Wait for all sub-payloads to complete across all partitions
        // The batchCount should eventually sum up to 10 (all distinct tsids)
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            PayloadStatus status = statusStore.get(bhatsJobId);
            // Wait until we have status and batch count is complete (10 distinct tsids)
            return status != null && status.batchCount == 10;
        });

        PayloadStatus status = statusStore.get(bhatsJobId);
        assertNotNull(status, "Status should be published for failed payload");
        assertFalse(status.success, "Payload should be marked as FAILURE due to forced failKey");
        assertEquals(10, status.batchCount, "Batch count should equal distinct key groups (10)");
    }
}
