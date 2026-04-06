package com.bookscanner.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuratie.
 *
 * Leerpunt: We serialiseren events zelf naar JSON-string met Jackson 3's ObjectMapper,
 * in plaats van spring-kafka's JsonSerializer. Dit voorkomt een Jackson 2/3
 * incompatibiliteit: spring-kafka's JsonSerializer gebruikt intern com.fasterxml.jackson.
 *
 * Voordeel: volledige controle over serialisatie, consistent met Jackson 3.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseProducerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return props;
    }

    /**
     * Generieke KafkaTemplate<String, String>: we serialiseren zelf naar JSON.
     * Zie ImageUploadService en ImageProcessingService voor gebruik.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        ProducerFactory<String, String> factory =
                new DefaultKafkaProducerFactory<>(baseProducerProps());
        return new KafkaTemplate<>(factory);
    }
}
