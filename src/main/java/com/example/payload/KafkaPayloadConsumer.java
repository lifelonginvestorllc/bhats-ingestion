package com.example.payload;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class KafkaPayloadConsumer {

    @Autowired
    private PayloadService payloadService;

    @KafkaListener(topics = "payload-topic", groupId = "payload-group")
    public void listen(ConsumerRecord<String, Record[]> record) {
        String payloadId = record.key(); // use producer-provided key as stable payloadId
        try {
            Record[] array = record.value();
            List<Record> list = Arrays.asList(array);
            payloadService.submitLargePayload(payloadId, list);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}