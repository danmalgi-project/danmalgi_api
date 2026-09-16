package com.danmalgi.backend.global.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@ExtendWith(MockitoExtension.class)
class RedisConfigRedisTemplateTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    void redisTemplate_key_serializer는_StringRedisSerializer이다() {
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
    }

    @Test
    void redisTemplate_value_serializer는_GenericJacksonJsonRedisSerializer이다() {
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        assertThat(template.getValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
    }

    @Test
    void redisTemplate_hashKey_serializer는_StringRedisSerializer이다() {
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
    }

    @Test
    void redisTemplate_hashValue_serializer는_GenericJacksonJsonRedisSerializer이다() {
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        assertThat(template.getHashValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
    }
}
