package com.example.payload;

import com.example.payload.bhpubwrt.BhpubwrtProducer;
import com.example.payload.bhpubwrt.PayloadStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaStatusConsumer {

    @Autowired
    private StatusStore statusStore;
    @Autowired
    private BhpubwrtProducer bhpubwrtProducer;

    @KafkaListener(topics = "payload-status", groupId = "payload-status-group-primary", containerFactory = "statusKafkaListenerContainerFactory")
    public void listenStatusPrimary(ConsumerRecord<String, PayloadStatus> record) {
        process(record, "cluster-1");
    }

    @KafkaListener(topics = "payload-status", groupId = "payload-status-group-secondary", containerFactory = "statusKafkaListenerContainerFactory")
    public void listenStatusSecondary(ConsumerRecord<String, PayloadStatus> record) {
        process(record, "cluster-2");
    }

    @KafkaListener(topics = "payload-status", groupId = "payload-status-group-tertiary", containerFactory = "statusKafkaListenerContainerFactory")
    public void listenStatusTertiary(ConsumerRecord<String, PayloadStatus> record) {
        process(record, "cluster-3");
    }

    private void process(ConsumerRecord<String, PayloadStatus> record, String clusterId) {
        PayloadStatus status = record.value();
        status.clusterId = clusterId; // annotate origin
        statusStore.add(status);
        bhpubwrtProducer.onStatus(status);
        System.out.printf("[STATUS] cluster=%s payloadId=%s success=%s batchCount=%d completedAt=%d%n",
                clusterId, status.payloadId, status.success, status.batchCount, status.completedAt);
    }
}
