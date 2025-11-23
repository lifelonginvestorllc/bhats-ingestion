package com.example.payload;

import com.example.payload.bhwrtam.KafkaPayloadProcessor;
import com.example.payload.common.TSValues;
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
                List<TSValues> records = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    TSValues r = new TSValues();
                    r.key = "key" + (i % 10);  // 10 different keys
                    r.value = "value" + i;
                    records.add(r);
                }
                payloadService.submitLargePayload("payload-" + p, records);
            }

            // Wait for all processing to complete (heuristic)
            TimeUnit.SECONDS.sleep(5);
        } finally {
            payloadService.shutdown();
            exec.shutdownNow();
        }
    }
}