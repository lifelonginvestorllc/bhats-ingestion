package com.example.payload;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KafkaPayloadProducer {

    private static final String TOPIC = "payload-topic";

    @Autowired
    private KafkaTemplate<String, List<Record>> kafkaTemplate;

    public void send(String key, List<Record> records) {
        kafkaTemplate.send(TOPIC, key, records);
    }
}