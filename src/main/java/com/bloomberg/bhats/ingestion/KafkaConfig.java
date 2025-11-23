package com.bloomberg.bhats.ingestion;

import com.bloomberg.bhats.ingestion.common.PayloadStatus;
import com.bloomberg.bhats.ingestion.common.Payload;
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
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig {

    private static final String INGEST_PAYLOAD_TOPIC = "ingest-payload-topic";
    private static final String REPLY_STATUS_TOPIC = "reply-status-topic";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.topic.payload.partitions:3}")
    private int payloadTopicPartitions;

    @Value("${spring.kafka.cluster2.bootstrap-servers:}")
    private String cluster2Bootstrap;
    @Value("${spring.kafka.cluster3.bootstrap-servers:}")
    private String cluster3Bootstrap;

    @Bean
    public NewTopic payloadRequestTopic() {
        return new NewTopic(INGEST_PAYLOAD_TOPIC, payloadTopicPartitions, (short) 1);
    }

    @Bean
    public NewTopic payloadStatusTopic() {
        return new NewTopic(REPLY_STATUS_TOPIC, 1, (short) 1);
    }

    @Bean
    public ConsumerFactory<String, Payload> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ingest-payload-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<Payload> deserializer = new JsonDeserializer<>(Payload.class);
        deserializer.addTrustedPackages("*");
        deserializer.ignoreTypeHeaders(); // we don't rely on type headers for generic List

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Payload> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Payload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    @Bean
    public ProducerFactory<String, Payload> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Payload> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

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

    @Bean
    public ProducerFactory<String, PayloadStatus> statusProducerFactoryCluster2() {
        if (!StringUtils.hasText(cluster2Bootstrap)) return null;
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster2Bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public ProducerFactory<String, PayloadStatus> statusProducerFactoryCluster3() {
        if (!StringUtils.hasText(cluster3Bootstrap)) return null;
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster3Bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, PayloadStatus> statusKafkaTemplateCluster2() {
        if (statusProducerFactoryCluster2() == null) return null;
        return new KafkaTemplate<>(statusProducerFactoryCluster2());
    }

    @Bean
    public KafkaTemplate<String, PayloadStatus> statusKafkaTemplateCluster3() {
        if (statusProducerFactoryCluster3() == null) return null;
        return new KafkaTemplate<>(statusProducerFactoryCluster3());
    }

    @Bean
    public ConsumerFactory<String, PayloadStatus> statusConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "reply-status-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Configure via properties only (no instance passed)
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bloomberg.bhats.ingestion");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.bloomberg.bhats.ingestion.common.PayloadStatus");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConsumerFactory<String, PayloadStatus> statusConsumerFactoryCluster2() {
        if (!StringUtils.hasText(cluster2Bootstrap)) return null;
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster2Bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "reply-status-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bloomberg.bhats.ingestion");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.bloomberg.bhats.ingestion.common.PayloadStatus");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConsumerFactory<String, PayloadStatus> statusConsumerFactoryCluster3() {
        if (!StringUtils.hasText(cluster3Bootstrap)) return null;
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster3Bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "reply-status-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bloomberg.bhats.ingestion");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.bloomberg.bhats.ingestion.common.PayloadStatus");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "statusKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, PayloadStatus> statusKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PayloadStatus> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(statusConsumerFactory());
        return factory;
    }

    @Bean(name = "statusKafkaListenerContainerFactoryCluster2")
    public ConcurrentKafkaListenerContainerFactory<String, PayloadStatus> statusKafkaListenerContainerFactoryCluster2() {
        if (statusConsumerFactoryCluster2() == null) return null;
        ConcurrentKafkaListenerContainerFactory<String, PayloadStatus> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(statusConsumerFactoryCluster2());
        return factory;
    }

    @Bean(name = "statusKafkaListenerContainerFactoryCluster3")
    public ConcurrentKafkaListenerContainerFactory<String, PayloadStatus> statusKafkaListenerContainerFactoryCluster3() {
        if (statusConsumerFactoryCluster3() == null) return null;
        ConcurrentKafkaListenerContainerFactory<String, PayloadStatus> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(statusConsumerFactoryCluster3());
        return factory;
    }
}
