package com.example.payload;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KafkaPayloadConsumer {

    @Autowired
    private PayloadService payloadService;

    @KafkaListener(topics = "payload-topic", groupId = "payload-group")
    public void listen(ConsumerRecord<String, List<Record>> record) {
        String payloadId = "payload-" + System.currentTimeMillis() + "-" + record.key();
        try {
            payloadService.submitLargePayload(payloadId, record.value());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}