package com.bloomberg.bhats.ingestion.bhwrtam;

import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BatchStatusTracker {
    private final ConcurrentMap<String, List<BatchStatus>> tracker = new ConcurrentHashMap<>();
    private final Sinks.Many<String> onCompleteSink;

    public BatchStatusTracker(Sinks.Many<String> onCompleteSink) {
        this.onCompleteSink = onCompleteSink;
    }

    public void init(String bhatsJobId, int batchCount) {
        tracker.put(bhatsJobId, Collections.synchronizedList(new ArrayList<>(Collections.nCopies(batchCount, null))));
    }

    public synchronized void update(String bhatsJobId, int index, BatchStatus status) {
        List<BatchStatus> statuses = tracker.get(bhatsJobId);
        statuses.set(index, status);
        if (statuses.stream().noneMatch(Objects::isNull)) {
            onCompleteSink.tryEmitNext(bhatsJobId);
        }
    }

    public boolean isSuccessful(String bhatsJobId) {
        return tracker.get(bhatsJobId).stream().allMatch(s -> s == BatchStatus.SUCCESS);
    }

    public void remove(String bhatsJobId) {
        tracker.remove(bhatsJobId);
    }
}