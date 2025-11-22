package com.example.payload;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaStatusConsumer {

    @Autowired
    private StatusStore statusStore;

    @KafkaListener(topics = "payload-status", groupId = "payload-status-group", containerFactory = "statusKafkaListenerContainerFactory")
    public void listenStatus(ConsumerRecord<String, PayloadCompletionStatus> record) {
        PayloadCompletionStatus status = record.value();
        statusStore.add(status);
        System.out.printf("[STATUS] payloadId=%s success=%s batchCount=%d completedAt=%d%n",
                status.payloadId, status.success, status.batchCount, status.completedAt);
    }
}
