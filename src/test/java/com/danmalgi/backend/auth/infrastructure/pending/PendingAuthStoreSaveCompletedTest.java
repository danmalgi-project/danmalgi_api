package com.danmalgi.backend.auth.infrastructure.pending;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class PendingAuthStoreSaveCompletedTest {

    private static final String SESSION_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private PendingAuthStore pendingAuthStore;

    @BeforeEach
    void setUp() {
        pendingAuthStore = new PendingAuthStore(redisTemplate);
    }

    @Test
    void saveCompleted_완료마커키에_userId를_30분TTL로_저장한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        pendingAuthStore.saveCompleted(SESSION_ID, 100L);

        verify(valueOperations).set("auth:registered:" + SESSION_ID, 100L, Duration.ofMinutes(30));
    }
}
