package com.example.payload;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KafkaPayloadProducer {

    private static final String REQUEST_TOPIC = "payload-topic";
    private static final String REPLY_TOPIC = "payload-status";

    @Autowired
    private KafkaTemplate<String, Record[]> kafkaTemplate;

    @Autowired
    private KafkaTemplate<String, PayloadCompletionStatus> statusKafkaTemplate;

    public void send(String key, List<Record> records) {
        kafkaTemplate.send(REQUEST_TOPIC, key, records.toArray(new Record[0]));
    }

    public void sendStatus(PayloadCompletionStatus status) {
        statusKafkaTemplate.send(REPLY_TOPIC, status.payloadId, status);
    }
}