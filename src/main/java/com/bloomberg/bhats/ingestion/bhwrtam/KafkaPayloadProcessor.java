package com.bloomberg.bhats.ingestion.bhwrtam;

import com.bloomberg.bhats.ingestion.common.DataPayload;
import com.bloomberg.bhats.ingestion.common.Payload;
import com.bloomberg.bhats.ingestion.common.StatusPublisher;
import com.bloomberg.bhats.ingestion.common.PayloadStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class KafkaPayloadProcessor {
    private final int NUM_QUEUES = 4;
    private final Map<Integer, BlockingQueue<BatchPayload>> queueMap = new HashMap<>();
    private final ExecutorService executorService;
    private BatchStatusTracker tracker;
    private final Sinks.Many<String> onCompleteSink = Sinks.many().unicast().onBackpressureBuffer();
    private final AtomicBoolean started = new AtomicBoolean(false);
    @Value("${payload.randomFailures:false}")
    private boolean randomFailures;
    @Value("${payload.failKey:}")
    private String failKey;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger completedPayloads = new AtomicInteger(0);
    private final AtomicInteger successfulPayloads = new AtomicInteger(0);
    private final List<String> successfulPayloadIds = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentMap<String, Integer> payloadBatchSizes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> payloadPartitionIds = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private StatusPublisher statusPublisher; // optional injection for status publishing

    // Default constructor used by Spring - creates a fixed thread pool (non-daemon)
    // for workers.
    public KafkaPayloadProcessor() {
        this.executorService = Executors.newFixedThreadPool(NUM_QUEUES);
        init();
    }

    // Package-private constructor for tests to inject a custom ExecutorService
    // (e.g., daemon threads).
    public KafkaPayloadProcessor(ExecutorService executorService) {
        this.executorService = executorService;
        init();
    }

    // Allow tests to shut down worker threads.
    public void shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            executorService.shutdownNow();
            try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @PostConstruct
    public void init() {
        if (!started.compareAndSet(false, true)) {
            return; // already initialized
        }
        tracker = new BatchStatusTracker(onCompleteSink);
        for (int i = 0; i < NUM_QUEUES; i++) {
            BlockingQueue<BatchPayload> queue = new LinkedBlockingQueue<>(100);
            queueMap.put(i, queue);
            int idx = i;
            executorService.submit(() -> workerLoop(queueMap.get(idx)));
        }

        Flux<String> completedFlux = onCompleteSink.asFlux();
        completedFlux.delayElements(Duration.ofMillis(100)).subscribe(this::handleCompletePayload);
    }

    private int route(String tsid) {
        return Math.abs(tsid.hashCode() % NUM_QUEUES);
    }

    public void submitKafkaPayload(Payload payload) throws InterruptedException {
        String bhatsJobId = payload.bhatsJobId;
        List<DataPayload> eventData = payload.dataPayloads;

        // Group DataPayloads by queueId (similar to PayloadSplitter)
        // Each DataPayload is assigned to a queue based on the hash of its tsid
        Map<Integer, List<DataPayload>> queueGroupedPayloads = new HashMap<>();

        for (DataPayload dataPayload : eventData) {
            int queueId = route(dataPayload.tsid);
            queueGroupedPayloads.computeIfAbsent(queueId, k -> new ArrayList<>()).add(dataPayload);
        }

        // Initialize tracker with the number of batches (one per queue that has data)
        int batchCount = queueGroupedPayloads.size();
        tracker.init(bhatsJobId, batchCount);
        payloadBatchSizes.put(bhatsJobId, batchCount); // track batch size per payload

        // Store partition ID if available (maybe null for direct calls or non-partitioned payloads)
        if (payload.partitionId != null) {
            payloadPartitionIds.put(bhatsJobId, payload.partitionId);
        }

        // Create BatchPayload for each queue and submit to the corresponding blocking queue
        for (Map.Entry<Integer, List<DataPayload>> entry : queueGroupedPayloads.entrySet()) {
            int queueId = entry.getKey();
            List<DataPayload> dataPayloads = entry.getValue();

            BatchPayload batch = new BatchPayload(bhatsJobId, queueId, dataPayloads);
            queueMap.get(queueId).put(batch);
        }
    }

    public int getCompletedPayloads() {
        return completedPayloads.get();
    }

    public int getSuccessfulPayloadsCount() {
        return successfulPayloads.get();
    }

    public List<String> getSuccessfulPayloadIds() {
        return new ArrayList<>(successfulPayloadIds);
    }

    private void workerLoop(BlockingQueue<BatchPayload> queue) {
        while (!shuttingDown.get() && !Thread.currentThread().isInterrupted()) {
            try {
                BatchPayload batchPayload = queue.poll(500, TimeUnit.MILLISECONDS);
                if (batchPayload == null) {
                    continue; // check shutdown periodically
                }
                try {
                    processBatch(batchPayload);
                    tracker.update(batchPayload.bhatsJobId, batchPayload.batchId, BatchStatus.SUCCESS);
                } catch (Exception e) {
                    tracker.update(batchPayload.bhatsJobId, batchPayload.batchId, BatchStatus.FAILURE);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processBatch(BatchPayload batch) {
        System.out.printf("Processing payload %s, batchId %d with %d dataPayloads%n", batch.bhatsJobId,
                batch.batchId, batch.dataPayloads.size());

        if (failKey != null && !failKey.isBlank()) {
            // Check if any tsid in the batch matches the failKey
            for (DataPayload dataPayload : batch.dataPayloads) {
                if (dataPayload.tsid.equals(failKey)) {
                    throw new RuntimeException("Forced failure for testing: key=" + failKey);
                }
            }
        }
        if (randomFailures && Math.random() < 0.05) {
            throw new RuntimeException("Simulated failure");
        }
    }

    private void handleCompletePayload(String bhatsJobId) {
        boolean success = tracker.isSuccessful(bhatsJobId);
        System.out.printf("Payload %s COMPLETED. Status: %s%n", bhatsJobId, success ? "SUCCESS" : "FAILURE");
        tracker.remove(bhatsJobId);
        completedPayloads.incrementAndGet();
        if (success) {
            successfulPayloads.incrementAndGet();
            successfulPayloadIds.add(bhatsJobId);
        }
        int batchSize = payloadBatchSizes.getOrDefault(bhatsJobId, 0);
        Integer partitionId = payloadPartitionIds.getOrDefault(bhatsJobId, null);
        if (statusPublisher != null) {
            // send single status; three consumer groups will each create their own
            // cluster-tagged reply
            PayloadStatus status = new PayloadStatus(bhatsJobId, success, batchSize, null);
            status.partitionId = partitionId;
            statusPublisher.publishStatus(status);
        }
        payloadBatchSizes.remove(bhatsJobId); // cleanup
        payloadPartitionIds.remove(bhatsJobId); // cleanup
    }
}