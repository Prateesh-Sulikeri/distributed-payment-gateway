package com.payments.payment_service.payments.config;

import com.payments.payment_service.payments.dto.PaymentResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuration class for Redis templates
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a RedisTemplate to store the CacheKey and PaymentResponse as key-value pairs in redis cache
     *
     * @param factory object of RedisConnectionFactory
     * @return RedisTemplate<String, PaymentResponse> the cache template
     */
    @Bean
    public RedisTemplate<String, PaymentResponse> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, PaymentResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJacksonJsonRedisSerializer(new JsonMapper()));

        return template;
    }
}
