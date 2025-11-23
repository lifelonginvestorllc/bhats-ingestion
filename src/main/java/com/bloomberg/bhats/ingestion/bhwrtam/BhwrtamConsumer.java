package com.bloomberg.bhats.ingestion.bhwrtam;

import com.bloomberg.bhats.ingestion.common.Payload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BhwrtamConsumer {

    @Autowired
    private KafkaPayloadProcessor kafkaPayloadProcessor;

    @KafkaListener(topics = "ingest-payload-topic", groupId = "ingest-payload-group")
    public void listen(ConsumerRecord<String, Payload> record) {
        try {
            kafkaPayloadProcessor.submitKafkaPayload(record.value());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}