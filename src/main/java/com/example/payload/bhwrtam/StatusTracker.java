package com.example.payload.bhwrtam;

import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class StatusTracker {
    private final ConcurrentMap<String, List<SubBatchStatus>> tracker = new ConcurrentHashMap<>();
    private final Sinks.Many<String> onCompleteSink;

    public StatusTracker(Sinks.Many<String> onCompleteSink) {
        this.onCompleteSink = onCompleteSink;
    }

    public void init(String bhatsJobId, int batchCount) {
        tracker.put(bhatsJobId, Collections.synchronizedList(new ArrayList<>(Collections.nCopies(batchCount, null))));
    }

    public synchronized void update(String bhatsJobId, int index, SubBatchStatus status) {
        List<SubBatchStatus> statuses = tracker.get(bhatsJobId);
        statuses.set(index, status);
        if (statuses.stream().noneMatch(Objects::isNull)) {
            onCompleteSink.tryEmitNext(bhatsJobId);
        }
    }

    public boolean isSuccessful(String bhatsJobId) {
        return tracker.get(bhatsJobId).stream().allMatch(s -> s == SubBatchStatus.SUCCESS);
    }

    public void remove(String bhatsJobId) {
        tracker.remove(bhatsJobId);
    }
}