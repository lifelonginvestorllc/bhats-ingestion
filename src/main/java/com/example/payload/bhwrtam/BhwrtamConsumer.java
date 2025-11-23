package com.example.payload.bhwrtam;

import com.example.payload.common.DataPayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class BhwrtamConsumer {

    @Autowired
    private KafkaPayloadProcessor kafkaPayloadProcessor;

    @KafkaListener(topics = "payload-topic", groupId = "payload-group")
    public void listen(ConsumerRecord<String, DataPayload[]> record) {
        String payloadId = record.key(); // use producer-provided key as stable payloadId
        try {
            DataPayload[] array = record.value();
            List<DataPayload> list = Arrays.asList(array);
            kafkaPayloadProcessor.submitLargePayload(payloadId, list);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}