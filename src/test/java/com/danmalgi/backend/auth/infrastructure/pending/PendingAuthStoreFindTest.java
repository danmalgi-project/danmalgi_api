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

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.user.domain.model.OauthType;

@ExtendWith(MockitoExtension.class)
class PendingAuthStoreFindTest {

    private static final String SESSION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PENDING_KEY = "auth:pending:" + SESSION_ID;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private PendingAuthStore pendingAuthStore;

    @BeforeEach
    void setUp() {
        pendingAuthStore = new PendingAuthStore(redisTemplate);
    }

    private PendingOAuthProfile profile() {
        return new PendingOAuthProfile(
                "test@gmail.com", "google-sub-123", OauthType.GOOGLE, null, null, null);
    }

    @Test
    void find_세션이_있으면_프로필을_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(PENDING_KEY)).thenReturn(profile());

        Optional<PendingOAuthProfile> result = pendingAuthStore.find(SESSION_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@gmail.com");
        assertThat(result.get().getIdentifyId()).isEqualTo("google-sub-123");
        assertThat(result.get().getOauthType()).isEqualTo(OauthType.GOOGLE);
    }

    @Test
    void find_TTL이_만료되어_미스면_empty를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(PENDING_KEY)).thenReturn(null);

        Optional<PendingOAuthProfile> result = pendingAuthStore.find(SESSION_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void find_타입이_불일치하면_ClassCastException없이_empty를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(PENDING_KEY)).thenReturn("legacy-garbage");

        Optional<PendingOAuthProfile> result = pendingAuthStore.find(SESSION_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void find_조회_키는_auth_pending_sessionId_형식이다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(PENDING_KEY)).thenReturn(profile());

        pendingAuthStore.find(SESSION_ID);

        verify(valueOperations).get(PENDING_KEY);
    }
}
