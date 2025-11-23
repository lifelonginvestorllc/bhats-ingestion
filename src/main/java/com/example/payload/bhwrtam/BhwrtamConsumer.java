package com.example.payload.bhwrtam;

import com.example.payload.common.TSValues;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class BhwrtamConsumer {

    @Autowired
    private KafkaPayloadProcessor kafkaPayloadProcessor;

    @KafkaListener(topics = "payload-topic", groupId = "payload-group-cluster1")
    public void listenCluster1(ConsumerRecord<String, TSValues[]> record) {
        handle(record, "cluster-1");
    }

    @KafkaListener(topics = "payload-topic", groupId = "payload-group-cluster2")
    public void listenCluster2(ConsumerRecord<String, TSValues[]> record) {
        handle(record, "cluster-2");
    }

    @KafkaListener(topics = "payload-topic", groupId = "payload-group-cluster3")
    public void listenCluster3(ConsumerRecord<String, TSValues[]> record) {
        handle(record, "cluster-3");
    }

    private void handle(ConsumerRecord<String, TSValues[]> record, String clusterId) {
        String payloadId = record.key() + "::" + clusterId; // embed cluster in id for processor
        try {
            TSValues[] array = record.value();
            kafkaPayloadProcessor.submitLargePayload(payloadId, Arrays.asList(array));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}