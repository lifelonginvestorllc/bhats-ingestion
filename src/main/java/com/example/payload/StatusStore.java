package com.example.payload;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StatusStore {
    private final Map<String, PayloadCompletionStatus> statuses = new ConcurrentHashMap<>();

    public void add(PayloadCompletionStatus status) {
        statuses.put(status.payloadId, status);
    }

    public Map<String, PayloadCompletionStatus> getAll() {
        return statuses;
    }

    public int size() {
        return statuses.size();
    }

    public PayloadCompletionStatus get(String payloadId) {
        return statuses.get(payloadId);
    }

    public void clear() {
        statuses.clear();
    }
}
