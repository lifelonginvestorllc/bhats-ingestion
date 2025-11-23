package com.example.payload;

import com.example.payload.common.PayloadStatus;
import com.example.payload.common.TSValues;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for bhwrtam (Consumer/Processor) module.
 * Configures:
 * - Consumer for consuming payloads (TSValues[])
 * - Producer for publishing status back to reply topic
 */
@EnableKafka
@Configuration
public class BhwrtamKafkaConfig {

    private static final String REQUEST_TOPIC = "payload-topic";
    private static final String REPLY_TOPIC = "payload-status";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // Topic creation beans (same topics as bhpubwrt)
    @Bean
    public NewTopic payloadRequestTopic() {
        return new NewTopic(REQUEST_TOPIC, 1, (short) 1);
    }

    @Bean
    public NewTopic payloadStatusTopic() {
        return new NewTopic(REPLY_TOPIC, 1, (short) 1);
    }

    // Consumer for TSValues[] payloads
    @Bean
    public ConsumerFactory<String, TSValues[]> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payload-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<TSValues[]> deserializer = new JsonDeserializer<>(TSValues[].class);
        deserializer.addTrustedPackages("*");
        deserializer.ignoreTypeHeaders();

        return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TSValues[]> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TSValues[]> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    // Producer for PayloadStatus (used by bhwrtam to reply with status)
    @Bean
    public ProducerFactory<String, PayloadStatus> statusProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, PayloadStatus> statusKafkaTemplate() {
        return new KafkaTemplate<>(statusProducerFactory());
    }
}

