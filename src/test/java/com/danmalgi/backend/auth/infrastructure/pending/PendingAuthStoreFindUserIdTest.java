package com.danmalgi.backend.auth.infrastructure.pending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.danmalgi.backend.user.domain.model.OauthType;

@ExtendWith(MockitoExtension.class)
class PendingAuthStoreFindUserIdTest {

    private static final String OAUTH_KEY = "auth:pending:oauth:1:google-sub-123";

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
    void findUserId_oauth키가_히트하면_userId를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OAUTH_KEY)).thenReturn(100L);

        Optional<Long> result =
                pendingAuthStore.findUserId(OauthType.GOOGLE.getNumber(), "google-sub-123");

        assertThat(result).contains(100L);
    }

    @Test
    void findUserId_Integer로_역직렬화되어도_Long으로_정규화한다() {
        // JSON 역직렬화는 작은 수를 Integer 로 돌려준다. Optional<Long> 계약이 깨지면
        // 호출부의 Long userId 대입에서 ClassCastException 이 난다.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OAUTH_KEY)).thenReturn(100);

        Optional<Long> result =
                pendingAuthStore.findUserId(OauthType.GOOGLE.getNumber(), "google-sub-123");

        assertThat(result).contains(100L);
    }

    @Test
    void findUserId_미스면_empty를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OAUTH_KEY)).thenReturn(null);

        Optional<Long> result =
                pendingAuthStore.findUserId(OauthType.GOOGLE.getNumber(), "google-sub-123");

        assertThat(result).isEmpty();
    }

    @Test
    void findUserId_숫자가_아닌_값이_들어있으면_empty를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OAUTH_KEY)).thenReturn("not-a-number");

        Optional<Long> result =
                pendingAuthStore.findUserId(OauthType.GOOGLE.getNumber(), "google-sub-123");

        assertThat(result).isEmpty();
    }

    @Test
    void findUserId_조회_키는_auth_pending_oauth_형식이다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OAUTH_KEY)).thenReturn(100L);

        pendingAuthStore.findUserId(OauthType.GOOGLE.getNumber(), "google-sub-123");

        verify(valueOperations).get(OAUTH_KEY);
    }
}
