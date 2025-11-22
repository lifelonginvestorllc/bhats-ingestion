package com.example.payload;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class PayloadService {
    private final int NUM_QUEUES = 4;
    private final Map<Integer, BlockingQueue<PayloadBatch>> queueMap = new HashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(NUM_QUEUES);
    private StatusTracker tracker;
    private final Sinks.Many<String> onCompleteSink = Sinks.many().unicast().onBackpressureBuffer();

    @PostConstruct
    public void init() {
        tracker = new StatusTracker(onCompleteSink);
        for (int i = 0; i < NUM_QUEUES; i++) {
            BlockingQueue<PayloadBatch> queue = new LinkedBlockingQueue<>(100);
            queueMap.put(i, queue);
            int idx = i;
            executorService.submit(() -> workerLoop(queueMap.get(idx)));
        }

        Flux<String> completedFlux = onCompleteSink.asFlux();
        completedFlux
                .delayElements(Duration.ofMillis(100))
                .subscribe(this::handleCompletePayload);
    }

    private int route(String key) {
        return Math.abs(key.hashCode() % NUM_QUEUES);
    }

    public void submitLargePayload(String payloadId, List<Record> records) throws InterruptedException {
        Map<String, List<Record>> grouped = records.stream()
                .collect(Collectors.groupingBy(r -> r.key));

        int index = 0;
        tracker.init(payloadId, grouped.size());

        for (Map.Entry<String, List<Record>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            int queueId = route(key);
            PayloadBatch batch = new PayloadBatch(payloadId, index++, key, entry.getValue());
            queueMap.get(queueId).put(batch);
        }
    }

    private void workerLoop(BlockingQueue<PayloadBatch> queue) {
        while (true) {
            try {
                PayloadBatch batch = queue.take();
                try {
                    processBatch(batch);
                    tracker.update(batch.payloadId, batch.index, BatchStatus.SUCCESS);
                } catch (Exception e) {
                    tracker.update(batch.payloadId, batch.index, BatchStatus.FAILURE);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processBatch(PayloadBatch batch) {
        System.out.printf("Processing payload %s, key %s, batch %d with %d records%n",
                batch.payloadId, batch.key, batch.index, batch.records.size());

        if (Math.random() < 0.05) {
            throw new RuntimeException("Simulated failure");
        }
    }

    private void handleCompletePayload(String payloadId) {
        boolean success = tracker.isSuccessful(payloadId);
        System.out.printf("Payload %s COMPLETED. Status: %s%n", payloadId, success ? "SUCCESS" : "FAILURE");
        tracker.remove(payloadId);
    }
}