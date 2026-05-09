package com.payments.mock_bank_service.config;

import com.payments.mock_bank_service.event.PaymentProcessedEvent;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;


@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, PaymentProcessedEvent> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();

        JacksonJsonSerializer<PaymentProcessedEvent> serializer =
                new JacksonJsonSerializer<>();

        serializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(
                props,
                new StringSerializer(),
                serializer
        );
    }

    @Bean
    public KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate(
            ProducerFactory<String, PaymentProcessedEvent> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }
}
