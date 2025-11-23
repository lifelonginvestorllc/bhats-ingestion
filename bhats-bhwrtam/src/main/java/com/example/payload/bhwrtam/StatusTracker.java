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

    public void init(String payloadId, int batchCount) {
        tracker.put(payloadId, Collections.synchronizedList(new ArrayList<>(Collections.nCopies(batchCount, null))));
    }

    public synchronized void update(String payloadId, int index, SubBatchStatus status) {
        List<SubBatchStatus> statuses = tracker.get(payloadId);
        statuses.set(index, status);
        if (statuses.stream().noneMatch(Objects::isNull)) {
            onCompleteSink.tryEmitNext(payloadId);
        }
    }

    public boolean isSuccessful(String payloadId) {
        return tracker.get(payloadId).stream().allMatch(s -> s == SubBatchStatus.SUCCESS);
    }

    public void remove(String payloadId) {
        tracker.remove(payloadId);
    }
}