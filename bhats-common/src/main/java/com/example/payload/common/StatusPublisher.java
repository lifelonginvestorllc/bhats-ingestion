package com.example.payload.common;

/**
 * Interface for publishing payload completion status.
 * This decouples bhwrtam (consumer/processor) from bhpubwrt (producer/aggregator).
 * bhwrtam calls this interface to publish status without knowing about bhpubwrt implementation.
 */
public interface StatusPublisher {
    /**
     * Publish the completion status of a payload.
     * @param status the payload status to publish
     */
    void publishStatus(PayloadStatus status);
}

