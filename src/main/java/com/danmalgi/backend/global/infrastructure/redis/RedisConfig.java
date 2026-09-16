package com.danmalgi.backend.global.infrastructure.redis;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
public class RedisConfig {
    // private static final String USER_CACHE_NAME = "user";

    // @Bean
    // public RedisConnectionFactory redisConnectionFactory(
    //         @Value("${spring.data.redis.host}") String host,
    //         @Value("${spring.data.redis.port}") int port,
    //         @Value("${spring.data.redis.password}") String password,
    //         @Value("${spring.data.redis.timeout}") long timeoutMillis
    // ) {
    //     RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(host, port);
    //     if (password != null && !password.isBlank()) {
    //         redisConfig.setPassword(RedisPassword.of(password));
    //     }

    //     LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
    //             .commandTimeout(Duration.ofMillis(timeoutMillis))
    //             .build();

    //     return new LettuceConnectionFactory(redisConfig, clientConfig);
    // }

    // @Bean
    // public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
    //     return new StringRedisTemplate(redisConnectionFactory);
    // }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonRedisSerializer());
        return template;
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory factory) {
        // 기본 TTL
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .computePrefixWith(cacheName -> cacheName + ":")
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(jsonRedisSerializer())
            );

        // 캐시 이름별 TTL 개별 지정
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("user", defaultConfig.entryTtl(Duration.ofMinutes(2)));

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)  // 캐시별 TTL 적용
            .build();
    }

    private GenericJacksonJsonRedisSerializer jsonRedisSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
            .enableDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfBaseType(Object.class)
                    .build()
            )
            .build();
    }
}
