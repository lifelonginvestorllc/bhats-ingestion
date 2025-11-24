package com.bloomberg.bhats.ingestion.bhpubwrt;

import com.bloomberg.bhats.ingestion.common.PayloadStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class PayloadStatusStore {
    private final Map<String, PayloadStatus> statuses = new ConcurrentHashMap<>();

    public void add(PayloadStatus status) {
        statuses.put(status.bhatsJobId, status);
    }

    public Map<String, PayloadStatus> getAll() {
        return statuses;
    }

    public int size() {
        // Count unique original job IDs (without partition suffix)
        return (int) statuses.keySet().stream()
            .map(this::extractOriginalJobId)
            .distinct()
            .count();
    }

    public PayloadStatus get(String bhatsJobId) {
        // First try direct lookup
        PayloadStatus directMatch = statuses.get(bhatsJobId);
        if (directMatch != null) {
            return directMatch;
        }

        // If not found, aggregate sub-payload statuses
        return getAggregated(bhatsJobId);
    }

    /**
     * Gets aggregated status for a job by combining all sub-payload statuses.
     *
     * @param originalJobId The original job ID (without a partition suffix)
     * @return Aggregated PayloadStatus or null if no sub-payloads found
     */
    public PayloadStatus getAggregated(String originalJobId) {
        Map<String, PayloadStatus> subPayloadStatuses = statuses.entrySet().stream()
            .filter(e -> extractOriginalJobId(e.getKey()).equals(originalJobId))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (subPayloadStatuses.isEmpty()) {
            return null;
        }

        // Aggregate all sub-payload statuses
        boolean allSuccess = subPayloadStatuses.values().stream().allMatch(s -> s.success);
        int totalBatchCount = subPayloadStatuses.values().stream().mapToInt(s -> s.batchCount).sum();
        long maxCompletedAt = subPayloadStatuses.values().stream().mapToLong(s -> s.completedAt).max().orElse(0);
        String clusterId = subPayloadStatuses.values().stream()
            .map(s -> s.clusterId)
            .filter(c -> c != null)
            .findFirst()
            .orElse(null);

        // Count unique batch IDs to verify all batches completed
        long uniqueBatches = subPayloadStatuses.values().stream()
            .map(s -> s.batchId)
            .filter(p -> p != null)
            .distinct()
            .count();

        PayloadStatus aggregated = new PayloadStatus();
        aggregated.bhatsJobId = originalJobId;
        aggregated.success = allSuccess;
        aggregated.batchCount = totalBatchCount;
        aggregated.completedAt = maxCompletedAt;
        aggregated.clusterId = clusterId;
        aggregated.batchId = null; // Aggregated status doesn't have a single batch

        System.out.printf("Aggregated status for %s: %d sub-payloads, %d unique batches, success=%b%n",
            originalJobId, subPayloadStatuses.size(), uniqueBatches, allSuccess);

        return aggregated;
    }

    public void clear() {
        statuses.clear();
    }

    /**
     * Extracts the original job ID from a sub-payload ID.
     * Sub-payload IDs have format: originalJobId-pN where N is partition number.
     *
     * @param subPayloadId The sub-payload job ID
     * @return The original job ID
     */
    private String extractOriginalJobId(String subPayloadId) {
        if (subPayloadId == null) {
            return subPayloadId;
        }
        int partitionSuffixIndex = subPayloadId.lastIndexOf("-p");
        if (partitionSuffixIndex > 0) {
            return subPayloadId.substring(0, partitionSuffixIndex);
        }
        return subPayloadId;
    }
}
