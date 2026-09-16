package com.danmalgi.backend.auth.infrastructure.pending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final String USER_KEY = "auth:pending:user:100";
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

    private PendingOAuthProfile profile() {
        return new PendingOAuthProfile(
                100L, "test@gmail.com", "google-sub-123", OauthType.GOOGLE.getNumber(), null);
    }

    /**
     * PendingOAuthProfile 에는 {@code @EqualsAndHashCode} 가 없다 (다른 도메인 모델과 동일).
     * 따라서 {@code verify(...).set(key, profile(), ttl)} 로는 매칭되지 않는다 — 캡처해서
     * 필드 단위로 검증한다.
     */
    private Map<String, Object> captureWrittenValues() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOperations, times(2))
                .set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        List<String> keys = keyCaptor.getAllValues();
        List<Object> values = valueCaptor.getAllValues();
        Map<String, Object> written = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            written.put(keys.get(i), values.get(i));
        }
        assertThat(ttlCaptor.getAllValues())
                .containsExactly(Duration.ofMinutes(30), Duration.ofMinutes(30));
        return written;
    }

    @Test
    void save_프로필_본체키와_oauth_인덱스키_둘_다_저장한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        pendingAuthStore.save(profile());

        assertThat(captureWrittenValues()).containsOnlyKeys(USER_KEY, OAUTH_KEY);
    }

    @Test
    void save_본체키에는_프로필이_그대로_저장된다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        pendingAuthStore.save(profile());

        Object stored = captureWrittenValues().get(USER_KEY);
        assertThat(stored).isInstanceOf(PendingOAuthProfile.class);
        PendingOAuthProfile storedProfile = (PendingOAuthProfile) stored;
        assertThat(storedProfile.getUserId()).isEqualTo(100L);
        assertThat(storedProfile.getEmail()).isEqualTo("test@gmail.com");
        assertThat(storedProfile.getIdentifyId()).isEqualTo("google-sub-123");
        assertThat(storedProfile.getOauthType()).isEqualTo(OauthType.GOOGLE.getNumber());
        assertThat(storedProfile.getProfileImageUrl()).isNull();
    }

    @Test
    void save_oauth_인덱스키에는_userId가_Long으로_저장된다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        pendingAuthStore.save(profile());

        assertThat(captureWrittenValues().get(OAUTH_KEY)).isEqualTo(100L);
    }

    @Test
    void save_TTL은_두_키_모두_30분이다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        pendingAuthStore.save(profile());

        // captureWrittenValues 안에서 TTL 을 검증한다.
        assertThat(captureWrittenValues()).hasSize(2);
    }

    @Test
    void save_키는_기존_user_와_device_prefix_와_겹치지_않는다() {
        // chat/webrtc 가 읽는 user:{id} / device:{deviceId} 공유 계약을 침범하면 안 된다.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        pendingAuthStore.save(profile());

        assertThat(captureWrittenValues().keySet())
                .allSatisfy(key -> assertThat(key).startsWith("auth:pending:"))
                .noneSatisfy(key -> assertThat(key).startsWith("user:"))
                .noneSatisfy(key -> assertThat(key).startsWith("device:"));
    }
}
