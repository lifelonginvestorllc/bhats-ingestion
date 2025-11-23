package com.bloomberg.bhats.ingestion.bhwrtam;

import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BatchStatusTracker {
    private final ConcurrentMap<String, Map<Integer, BatchStatus>> tracker = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> expectedBatchCounts = new ConcurrentHashMap<>();
    private final Sinks.Many<String> onCompleteSink;

    public BatchStatusTracker(Sinks.Many<String> onCompleteSink) {
        this.onCompleteSink = onCompleteSink;
    }

    public void init(String bhatsJobId, int batchCount) {
        tracker.put(bhatsJobId, new ConcurrentHashMap<>());
        expectedBatchCounts.put(bhatsJobId, batchCount);
    }

    public synchronized void update(String bhatsJobId, int batchId, BatchStatus status) {
        Map<Integer, BatchStatus> statuses = tracker.get(bhatsJobId);
        statuses.put(batchId, status);
        int expectedCount = expectedBatchCounts.get(bhatsJobId);
        if (statuses.size() == expectedCount) {
            onCompleteSink.tryEmitNext(bhatsJobId);
        }
    }

    public boolean isSuccessful(String bhatsJobId) {
        return tracker.get(bhatsJobId).values().stream().allMatch(s -> s == BatchStatus.SUCCESS);
    }

    public void remove(String bhatsJobId) {
        tracker.remove(bhatsJobId);
        expectedBatchCounts.remove(bhatsJobId);
    }
}