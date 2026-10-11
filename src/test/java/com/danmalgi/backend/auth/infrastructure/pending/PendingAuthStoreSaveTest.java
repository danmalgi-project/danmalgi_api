package com.danmalgi.backend.auth.infrastructure.pending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.user.domain.model.OauthType;

@ExtendWith(MockitoExtension.class)
class PendingAuthStoreSaveTest {

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
    void save_세션id키로_프로필을_그대로_저장한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        pendingAuthStore.save(SESSION_ID, profile());

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).set(eq(PENDING_KEY), valueCaptor.capture(), eq(Duration.ofMinutes(30)));

        Object stored = valueCaptor.getValue();
        assertThat(stored).isInstanceOf(PendingOAuthProfile.class);
        PendingOAuthProfile storedProfile = (PendingOAuthProfile) stored;
        assertThat(storedProfile.getEmail()).isEqualTo("test@gmail.com");
        assertThat(storedProfile.getIdentifyId()).isEqualTo("google-sub-123");
        assertThat(storedProfile.getOauthType()).isEqualTo(OauthType.GOOGLE);
    }

    @Test
    void save_키는_기존_user_와_device_prefix_와_겹치지_않는다() {
        // chat/webrtc 가 읽는 user:{id} / device:{deviceId} 공유 계약을 침범하면 안 된다.
        assertThat(PENDING_KEY).startsWith("auth:pending:");
        assertThat(PENDING_KEY).doesNotStartWith("user:");
        assertThat(PENDING_KEY).doesNotStartWith("device:");
    }
}
