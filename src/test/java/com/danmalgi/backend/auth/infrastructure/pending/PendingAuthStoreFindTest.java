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

    private static final String USER_KEY = "auth:pending:user:100";

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
                100L, "test@gmail.com", "google-sub-123", OauthType.GOOGLE.getNumber(), null);
    }

    @Test
    void find_세션이_있으면_프로필을_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(profile());

        Optional<PendingOAuthProfile> result = pendingAuthStore.find(100L);

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(100L);
        assertThat(result.get().getEmail()).isEqualTo("test@gmail.com");
        assertThat(result.get().getIdentifyId()).isEqualTo("google-sub-123");
        assertThat(result.get().getOauthType()).isEqualTo(OauthType.GOOGLE.getNumber());
    }

    @Test
    void find_TTL이_만료되어_미스면_empty를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(null);

        Optional<PendingOAuthProfile> result = pendingAuthStore.find(100L);

        assertThat(result).isEmpty();
    }

    @Test
    void find_타입이_불일치하면_ClassCastException없이_empty를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn("legacy-garbage");

        Optional<PendingOAuthProfile> result = pendingAuthStore.find(100L);

        assertThat(result).isEmpty();
    }

    @Test
    void find_조회_키는_auth_pending_user_형식이다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(USER_KEY)).thenReturn(profile());

        pendingAuthStore.find(100L);

        verify(valueOperations).get(USER_KEY);
    }
}
