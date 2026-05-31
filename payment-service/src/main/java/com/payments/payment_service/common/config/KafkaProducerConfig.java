package com.payments.payment_service.common.config;

import com.payments.payment_service.payment.event.PaymentInitiatedEvent;
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
    public ProducerFactory<String, PaymentInitiatedEvent> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = kafkaProperties.buildProducerProperties();

        JacksonJsonSerializer<PaymentInitiatedEvent> serializer =
                new JacksonJsonSerializer<>();

        serializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(
                config,
                new StringSerializer(),
                serializer
        );
    }

    @Bean
    public KafkaTemplate<String, PaymentInitiatedEvent> kafkaTemplate(
            ProducerFactory<String, PaymentInitiatedEvent> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }
}
