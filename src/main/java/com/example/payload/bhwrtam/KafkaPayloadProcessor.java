package com.example.payload.bhwrtam;

import com.example.payload.SubBatch;
import com.example.payload.common.TSValues;
import com.example.payload.bhpubwrt.BhpubwrtProducer;
import com.example.payload.bhpubwrt.PayloadStatus;
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
	private final Map<Integer, BlockingQueue<SubBatch>> queueMap = new HashMap<>();
	private final ExecutorService executorService;
	private StatusTracker tracker;
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

	@Autowired(required = false)
	private BhpubwrtProducer bhpubwrtProducer; // optional injection for status publishing

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
		tracker = new StatusTracker(onCompleteSink);
		for (int i = 0; i < NUM_QUEUES; i++) {
			BlockingQueue<SubBatch> queue = new LinkedBlockingQueue<>(100);
			queueMap.put(i, queue);
			int idx = i;
			executorService.submit(() -> workerLoop(queueMap.get(idx)));
		}

		Flux<String> completedFlux = onCompleteSink.asFlux();
		completedFlux.delayElements(Duration.ofMillis(100)).subscribe(this::handleCompletePayload);
	}

	private int route(String key) {
		return Math.abs(key.hashCode() % NUM_QUEUES);
	}

	public void submitLargePayload(String payloadId, List<TSValues> records) throws InterruptedException {
		Map<String, List<TSValues>> grouped = records.stream().collect(Collectors.groupingBy(r -> r.key));

		int index = 0;
		tracker.init(payloadId, grouped.size());
		payloadBatchSizes.put(payloadId, grouped.size()); // track batch size per payload

		for (Map.Entry<String, List<TSValues>> entry : grouped.entrySet()) {
			String key = entry.getKey();
			int queueId = route(key);
			SubBatch batch = new SubBatch(payloadId, index++, key, entry.getValue());
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

	private void workerLoop(BlockingQueue<SubBatch> queue) {
		while (!shuttingDown.get() && !Thread.currentThread().isInterrupted()) {
			try {
				SubBatch batch = queue.poll(500, TimeUnit.MILLISECONDS);
				if (batch == null) {
					continue; // check shutdown periodically
				}
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

	private void processBatch(SubBatch batch) {
		System.out.printf("Processing payload %s, key %s, batch %d with %d records%n", batch.payloadId, batch.key,
				batch.index, batch.records.size());

		if (failKey != null && !failKey.isBlank() && batch.key.equals(failKey)) {
			throw new RuntimeException("Forced failure for testing: key=" + failKey);
		}
		if (randomFailures && Math.random() < 0.05) {
			throw new RuntimeException("Simulated failure");
		}
	}

	private void handleCompletePayload(String payloadId) {
		boolean success = tracker.isSuccessful(payloadId);
		System.out.printf("Payload %s COMPLETED. Status: %s%n", payloadId, success ? "SUCCESS" : "FAILURE");
		tracker.remove(payloadId);
		completedPayloads.incrementAndGet();
		if (success) {
			successfulPayloads.incrementAndGet();
			successfulPayloadIds.add(payloadId);
		}
		int batchSize = payloadBatchSizes.getOrDefault(payloadId, 0);
		if (bhpubwrtProducer != null) {
			// send single status; three consumer groups will each create their own
			// cluster-tagged reply
			bhpubwrtProducer.sendStatus(new PayloadStatus(payloadId, success, batchSize, null));
		}
		payloadBatchSizes.remove(payloadId); // cleanup
	}
}