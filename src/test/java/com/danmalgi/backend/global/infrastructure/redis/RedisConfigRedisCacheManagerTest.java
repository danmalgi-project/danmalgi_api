package com.danmalgi.backend.global.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@ExtendWith(MockitoExtension.class)
class RedisConfigRedisCacheManagerTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    void redisCacheManager_value는_JSON_형식으로_직렬화된다() {
        RedisCacheManager cacheManager = redisConfig.redisCacheManager(connectionFactory);
        cacheManager.afterPropertiesSet();

        RedisCacheConfiguration config = cacheManager
                .getCacheConfigurations().values().stream()
                .findFirst()
                .orElseThrow();

        RedisSerializationContext.SerializationPair<Object> pair =
                (RedisSerializationContext.SerializationPair<Object>) config.getValueSerializationPair();

        Map<String, String> testData = Map.of("key", "value");
        ByteBuffer buffer = pair.write(testData);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String serialized = new String(bytes);

        assertThat(serialized).contains("\"key\"");
        assertThat(serialized).contains("\"value\"");
        // JDK 직렬화의 매직 바이트(0xACED)로 시작하지 않아야 한다
        assertThat(bytes[0]).isNotEqualTo((byte) 0xAC);
    }
}
