package com.example.payload;

import com.example.payload.bhwrtam.KafkaPayloadProcessor;
import com.example.payload.common.DataPayload;
import com.example.payload.common.Datapoint;
import com.example.payload.common.Payload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class KafkaPayloadProcessorTest {

    @Test
    public void testMultiplePayloadsProcessing() throws InterruptedException {
        // Create an executor with daemon threads so the JVM can exit cleanly if something goes wrong.
        ExecutorService exec = Executors.newFixedThreadPool(4, new ThreadFactory() {
            private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = defaultFactory.newThread(r);
                t.setDaemon(true);
                return t;
            }
        });

        KafkaPayloadProcessor payloadService = new KafkaPayloadProcessor(exec);

        try {
            for (int p = 1; p <= 3; p++) {
                List<DataPayload> records = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    DataPayload r = new DataPayload();
                    r.tsid = "tsid" + (i % 10);  // 10 different keys
                    Datapoint dp = new Datapoint();
                    dp.value = "datapoint" + i;
                    r.datapoints = List.of(dp);
                    records.add(r);
                }
                Payload payload = new Payload("payload-" + p, records);
                payloadService.submitLargePayload(payload);
            }

            // Wait for all processing to complete (heuristic)
            TimeUnit.SECONDS.sleep(5);
        } finally {
            payloadService.shutdown();
            exec.shutdownNow();
        }
    }
}