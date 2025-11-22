package com.example.payload;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaStatusConsumer {

    @Autowired
    private StatusStore statusStore;
    @Autowired
    private KafkaPayloadProducer producer;

    @KafkaListener(topics = "payload-status", groupId = "payload-status-group-primary", containerFactory = "statusKafkaListenerContainerFactory")
    public void listenStatusPrimary(ConsumerRecord<String, CompletionStatus> record) {
        process(record, "cluster-1");
    }

    @KafkaListener(topics = "payload-status", groupId = "payload-status-group-secondary", containerFactory = "statusKafkaListenerContainerFactory")
    public void listenStatusSecondary(ConsumerRecord<String, CompletionStatus> record) {
        process(record, "cluster-2");
    }

    @KafkaListener(topics = "payload-status", groupId = "payload-status-group-tertiary", containerFactory = "statusKafkaListenerContainerFactory")
    public void listenStatusTertiary(ConsumerRecord<String, CompletionStatus> record) {
        process(record, "cluster-3");
    }

    private void process(ConsumerRecord<String, CompletionStatus> record, String clusterId) {
        CompletionStatus status = record.value();
        status.clusterId = clusterId; // annotate origin
        statusStore.add(status);
        producer.onStatus(status);
        System.out.printf("[STATUS] cluster=%s payloadId=%s success=%s batchCount=%d completedAt=%d%n",
                clusterId, status.payloadId, status.success, status.batchCount, status.completedAt);
    }
}
