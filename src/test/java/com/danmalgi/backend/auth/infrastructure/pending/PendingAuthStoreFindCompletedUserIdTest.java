package com.danmalgi.backend.auth.infrastructure.pending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class PendingAuthStoreFindCompletedUserIdTest {

    private static final String SESSION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String REGISTERED_KEY = "auth:registered:" + SESSION_ID;

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
    void findCompletedUserId_완료마커가_있으면_userId를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REGISTERED_KEY)).thenReturn(100L);

        Optional<Long> result = pendingAuthStore.findCompletedUserId(SESSION_ID);

        assertThat(result).contains(100L);
    }

    @Test
    void findCompletedUserId_값이_Integer로_역직렬화돼도_Long으로_반환한다() {
        // JSON 역직렬화가 작은 수를 Integer 로 돌려줄 수 있다.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REGISTERED_KEY)).thenReturn(100);

        Optional<Long> result = pendingAuthStore.findCompletedUserId(SESSION_ID);

        assertThat(result).contains(100L);
    }

    @Test
    void findCompletedUserId_마커가_없으면_empty를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REGISTERED_KEY)).thenReturn(null);

        Optional<Long> result = pendingAuthStore.findCompletedUserId(SESSION_ID);

        assertThat(result).isEmpty();
    }
}
