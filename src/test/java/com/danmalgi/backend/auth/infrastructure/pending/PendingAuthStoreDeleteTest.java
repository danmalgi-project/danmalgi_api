package com.danmalgi.backend.auth.infrastructure.pending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.user.domain.model.OauthType;

@ExtendWith(MockitoExtension.class)
class PendingAuthStoreDeleteTest {

    private static final String USER_KEY = "auth:pending:user:100";
    private static final String OAUTH_KEY = "auth:pending:oauth:1:google-sub-123";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private PendingAuthStore pendingAuthStore;

    @BeforeEach
    void setUp() {
        pendingAuthStore = new PendingAuthStore(redisTemplate);
    }

    private PendingOAuthProfile profile() {
        return new PendingOAuthProfile(
                100L, "test@gmail.com", "google-sub-123", OauthType.GOOGLE.getNumber(), null);
    }

    private List<String> captureDeletedKeys() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(2)).delete(keyCaptor.capture());
        return keyCaptor.getAllValues();
    }

    @Test
    void delete_본체키와_oauth_인덱스키를_모두_삭제한다() {
        pendingAuthStore.delete(profile());

        assertThat(captureDeletedKeys()).containsExactlyInAnyOrder(USER_KEY, OAUTH_KEY);
    }

    @Test
    void delete_oauth_인덱스키만_남기지_않는다() {
        // 인덱스 키가 남으면 TTL 내 재로그인이 존재하지 않는 pending 세션의 userId 를
        // 재사용해 이미 INSERT 된 행과 충돌한다.
        pendingAuthStore.delete(profile());

        assertThat(captureDeletedKeys()).contains(OAUTH_KEY);
    }
}
