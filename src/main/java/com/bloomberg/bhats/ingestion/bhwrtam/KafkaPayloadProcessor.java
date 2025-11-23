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
import java.util.stream.Collectors;

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
		Map<String, List<DataPayload>> grouped = eventData.stream().collect(Collectors.groupingBy(r -> r.tsid));

		int index = 0;
		tracker.init(bhatsJobId, grouped.size());
		payloadBatchSizes.put(bhatsJobId, grouped.size()); // track batch size per payload
		// Store partition ID if available (may be null for direct calls or non-partitioned payloads)
		if (payload.partitionId != null) {
			payloadPartitionIds.put(bhatsJobId, payload.partitionId);
		}

		for (Map.Entry<String, List<DataPayload>> entry : grouped.entrySet()) {
			String key = entry.getKey();
			int queueId = route(key);
			BatchPayload batch = new BatchPayload(bhatsJobId, index++, key, entry.getValue());
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
				BatchPayload batch = queue.poll(500, TimeUnit.MILLISECONDS);
				if (batch == null) {
					continue; // check shutdown periodically
				}
				try {
					processBatch(batch);
					tracker.update(batch.bhatsJobId, batch.batchId, BatchStatus.SUCCESS);
				} catch (Exception e) {
					tracker.update(batch.bhatsJobId, batch.batchId, BatchStatus.FAILURE);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void processBatch(BatchPayload batch) {
		System.out.printf("Processing payload %s, key %s, batch %d with %d dataPayloads%n", batch.bhatsJobId, batch.key,
				batch.batchId, batch.dataPayloads.size());

		if (failKey != null && !failKey.isBlank() && batch.key.equals(failKey)) {
			throw new RuntimeException("Forced failure for testing: key=" + failKey);
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