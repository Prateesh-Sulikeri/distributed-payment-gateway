package com.payments.settlement_service.config;

import com.payments.settlement_service.event.PaymentSucceededEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, PaymentSucceededEvent> consumerFactory(
            KafkaProperties kafkaProperties
    ) {
        Map<String, Object> props =  kafkaProperties.buildConsumerProperties();

        JacksonJsonDeserializer<PaymentSucceededEvent> deserializer = new JacksonJsonDeserializer<>(PaymentSucceededEvent.class);

        deserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<
            String,
            PaymentSucceededEvent> kafkaListenerContainerFactory (
                    ConsumerFactory<String, PaymentSucceededEvent> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<
                String,
                PaymentSucceededEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        return factory;
    }
}
