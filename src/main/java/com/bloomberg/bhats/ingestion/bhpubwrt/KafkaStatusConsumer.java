package com.bloomberg.bhats.ingestion.bhpubwrt;

import com.bloomberg.bhats.ingestion.common.PayloadStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaStatusConsumer {

    @Autowired
    private PayloadStatusStore statusStore;
    @Autowired
    private BhpubwrtProducer bhpubwrtProducer;

    @KafkaListener(topics = "reply-status-topic", groupId = "reply-status-group-primary", containerFactory = "statusKafkaListenerContainerFactory")
    public void listenStatusPrimary(ConsumerRecord<String, PayloadStatus> record) {
        process(record, "cluster-1");
    }

    @KafkaListener(topics = "reply-status-topic", groupId = "reply-status-group-secondary", containerFactory = "statusKafkaListenerContainerFactory")
    public void listenStatusSecondary(ConsumerRecord<String, PayloadStatus> record) {
        process(record, "cluster-2");
    }

    @KafkaListener(topics = "reply-status-topic", groupId = "reply-status-group-tertiary", containerFactory = "statusKafkaListenerContainerFactory")
    public void listenStatusTertiary(ConsumerRecord<String, PayloadStatus> record) {
        process(record, "cluster-3");
    }

    private void process(ConsumerRecord<String, PayloadStatus> record, String clusterId) {
        PayloadStatus status = record.value();
        status.clusterId = clusterId; // annotate origin
        statusStore.add(status);
        bhpubwrtProducer.onStatus(status);
        System.out.printf("[STATUS] cluster=%s bhatsJobId=%s success=%s batchCount=%d completedAt=%d%n",
                clusterId, status.bhatsJobId, status.success, status.batchCount, status.completedAt);
    }
}
