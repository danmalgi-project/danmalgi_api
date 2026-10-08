package com.danmalgi.backend.auth.infrastructure.replay;

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
class OAuthNonceReplayStoreMarkUsedTest {

    private static final String NONCE = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
    private static final String KEY = "auth:oauth:used-nonce:APPLE:" + NONCE;
    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private OAuthNonceReplayStore store;

    @BeforeEach
    void setUp() {
        store = new OAuthNonceReplayStore(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void markUsed_처음_쓰는_nonce면_true() {
        when(valueOperations.setIfAbsent(KEY, Boolean.TRUE, TTL)).thenReturn(true);

        assertThat(store.markUsed(OauthType.APPLE, NONCE, TTL)).isTrue();
    }

    @Test
    void markUsed_이미_쓴_nonce면_false() {
        when(valueOperations.setIfAbsent(KEY, Boolean.TRUE, TTL)).thenReturn(false);

        assertThat(store.markUsed(OauthType.APPLE, NONCE, TTL)).isFalse();
    }

    @Test
    void markUsed_setIfAbsent가_null이면_거절쪽인_false() {
        when(valueOperations.setIfAbsent(KEY, Boolean.TRUE, TTL)).thenReturn(null);

        assertThat(store.markUsed(OauthType.APPLE, NONCE, TTL)).isFalse();
    }

    @Test
    void markUsed_auth_prefix_키에_받은_TTL로_SETNX한다() {
        when(valueOperations.setIfAbsent(KEY, Boolean.TRUE, TTL)).thenReturn(true);

        store.markUsed(OauthType.APPLE, NONCE, TTL);

        verify(valueOperations).setIfAbsent("auth:oauth:used-nonce:APPLE:" + NONCE, Boolean.TRUE, Duration.ofMinutes(10));
    }
}
