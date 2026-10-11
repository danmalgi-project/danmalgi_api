package com.danmalgi.backend.auth.infrastructure.pending;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

@ExtendWith(MockitoExtension.class)
class PendingAuthStoreDeleteTest {

    private static final String SESSION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PENDING_KEY = "auth:pending:" + SESSION_ID;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private PendingAuthStore pendingAuthStore;

    @BeforeEach
    void setUp() {
        pendingAuthStore = new PendingAuthStore(redisTemplate);
    }

    @Test
    void delete_세션id키를_삭제한다() {
        pendingAuthStore.delete(SESSION_ID);

        verify(redisTemplate).delete(PENDING_KEY);
    }
}
