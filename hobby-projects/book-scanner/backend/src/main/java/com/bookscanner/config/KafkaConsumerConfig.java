package com.bookscanner.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuratie.
 *
 * Leerpunt: In Spring Boot 4 / Jackson 3 gebruiken we StringDeserializer voor
 * alle Kafka consumers en deserializeren we het JSON payload handmatig met
 * Jackson 3's ObjectMapper. Dit vermijdt een incompatibiliteit: spring-kafka's
 * JsonDeserializer gebruikt intern nog com.fasterxml.jackson (Jackson 2) types,
 * die in Jackson 3 naar tools.jackson zijn verplaatst.
 *
 * Dit patroon — raw String consumeren, zelf parsen — geeft ook meer controle:
 * je kunt logging, schema validatie of dead-letter handling toevoegen.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, String> imageSubmittedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProps(groupId));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> imageSubmittedListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(imageSubmittedConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> bookRecognizedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProps(groupId + "-sse"));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> bookRecognizedListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(bookRecognizedConsumerFactory());
        return factory;
    }

    private Map<String, Object> consumerProps(String consumerGroupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }
}
