package com.example.payload.bhwrtam;

import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class StatusTracker {
    private final ConcurrentMap<String, List<SubBatchStatus>> tracker = new ConcurrentHashMap<>();
    private final Sinks.Many<String> onCompleteSink;
    private final Set<String> emitted = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public StatusTracker(Sinks.Many<String> onCompleteSink) {
        this.onCompleteSink = onCompleteSink;
    }

    public void init(String payloadId, int batchCount) {
        tracker.put(payloadId, Collections.synchronizedList(new ArrayList<>(Collections.nCopies(batchCount, null))));
        emitted.remove(payloadId); // reset if reused
    }

    public synchronized void update(String payloadId, int index, SubBatchStatus status) {
        List<SubBatchStatus> statuses = tracker.get(payloadId);
        if (statuses == null) return; // already cleaned up
        statuses.set(index, status);
        boolean complete = statuses.stream().noneMatch(Objects::isNull);
        if (complete && emitted.add(payloadId)) {
            onCompleteSink.tryEmitNext(payloadId);
        }
    }

    public boolean isSuccessful(String payloadId) {
        List<SubBatchStatus> list = tracker.get(payloadId);
        return list != null && list.stream().allMatch(s -> s == SubBatchStatus.SUCCESS);
    }

    public void remove(String payloadId) {
        tracker.remove(payloadId);
        emitted.remove(payloadId);
    }
}