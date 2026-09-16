package com.danmalgi.backend.auth.infrastructure.pending;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.danmalgi.backend.user.domain.model.OauthType;

@ExtendWith(MockitoExtension.class)
class PendingAuthStoreClaimUserIdTest {

    private static final String OAUTH_KEY = "auth:pending:oauth:1:google-sub-123";
    private static final Duration PENDING_TTL = Duration.ofMinutes(30);

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
    void claimUserId_선점_성공하면_true를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(OAUTH_KEY, 100L, PENDING_TTL)).thenReturn(true);

        boolean result =
                pendingAuthStore.claimUserId(OauthType.GOOGLE.getNumber(), "google-sub-123", 100L);

        assertThat(result).isTrue();
    }

    @Test
    void claimUserId_이미_선점되어_있으면_false를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(OAUTH_KEY, 100L, PENDING_TTL)).thenReturn(false);

        boolean result =
                pendingAuthStore.claimUserId(OauthType.GOOGLE.getNumber(), "google-sub-123", 100L);

        assertThat(result).isFalse();
    }

    @Test
    void claimUserId_setIfAbsent가_null이면_NPE없이_false를_반환한다() {
        // setIfAbsent 의 반환 타입은 Boolean 이다. 파이프라인/트랜잭션 모드에서 null 이 온다.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(OAUTH_KEY, 100L, PENDING_TTL)).thenReturn(null);

        boolean result =
                pendingAuthStore.claimUserId(OauthType.GOOGLE.getNumber(), "google-sub-123", 100L);

        assertThat(result).isFalse();
    }

    @Test
    void claimUserId_SETNX를_30분_TTL과_함께_호출한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(OAUTH_KEY, 100L, PENDING_TTL)).thenReturn(true);

        pendingAuthStore.claimUserId(OauthType.GOOGLE.getNumber(), "google-sub-123", 100L);

        verify(valueOperations).setIfAbsent(OAUTH_KEY, 100L, Duration.ofMinutes(30));
    }
}
