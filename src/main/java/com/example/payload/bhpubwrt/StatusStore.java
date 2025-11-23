package com.example.payload.bhpubwrt;

import com.example.payload.common.PayloadStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StatusStore {
    private final Map<String, PayloadStatus> statuses = new ConcurrentHashMap<>();

    public void add(PayloadStatus status) {
        statuses.put(status.bhatsJobId, status);
    }

    public Map<String, PayloadStatus> getAll() {
        return statuses;
    }

    public int size() {
        return statuses.size();
    }

    public PayloadStatus get(String bhatsJobId) {
        return statuses.get(bhatsJobId);
    }

    public void clear() {
        statuses.clear();
    }
}
