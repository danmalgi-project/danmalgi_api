package com.danmalgi.backend.auth.service;

import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.exception.UnsupportedOauthTypeException;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.grpc.dto.AuthorizeResponse;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.device.service.DeviceService;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceAuthorizeTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private DeviceJpaRepository deviceJpaRepository;

    @Mock
    private DeviceService deviceService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private OAuthPlatformAuthorizationPort googleOauthPort;

    @Mock
    private R2Uploader r2Uploader;

    @Mock
    private PendingAuthStore pendingAuthStore;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(googleOauthPort.supportedOauthType()).thenReturn(OauthType.GOOGLE);
        authService = new AuthService(
                userJpaRepository,
                deviceJpaRepository,
                deviceService,
                jwtTokenProvider,
                List.of(googleOauthPort),
                r2Uploader,
                pendingAuthStore
        );
    }

    private PendingOAuthProfile pendingProfile() {
        return new PendingOAuthProfile(
                null, "test@gmail.com", "google-sub-123", OauthType.GOOGLE.getNumber(), null);
    }

    @Test
    void authorize_지원하지_않는_OauthType이면_예외발생() {
        assertThatThrownBy(() -> authService.authorize("id-token", "device-1", OauthType.KAKAO))
                .isInstanceOf(UnsupportedOauthTypeException.class);
    }

    @Test
    void authorize_OAuthPort가_null_반환하면_예외발생() {
        // Kakao/Naver 어댑터는 여전히 미구현(null 반환)이라 이 경로로 실패한다.
        when(googleOauthPort.authorize(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.authorize("id-token", "device-1", OauthType.GOOGLE))
                .isInstanceOf(OauthAuthorizeFailException.class);
    }

    @Test
    void authorize_신규계정이면_users_행을_저장하지_않고_pending_세션만_만든다() {
        when(googleOauthPort.authorize("id-token")).thenReturn(pendingProfile());
        when(userJpaRepository.findByOauthTypeAndIdentifyId(OauthType.GOOGLE.getNumber(), "google-sub-123"))
                .thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(OauthType.GOOGLE.getNumber(), "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userJpaRepository.nextUserId()).thenReturn(100L);
        when(pendingAuthStore.claimUserId(OauthType.GOOGLE.getNumber(), "google-sub-123", 100L))
                .thenReturn(true);
        when(jwtTokenProvider.generateToken(100L, "device-1")).thenReturn("jwt-token");

        AuthorizeResponse response = authService.authorize("id-token", "device-1", OauthType.GOOGLE);

        assertThat(response.isPending()).isTrue();
        assertThat(response.user()).isNull();
        assertThat(response.pendingProfile().getUserId()).isEqualTo(100L);
        assertThat(response.jwtToken()).isEqualTo("jwt-token");
        verify(userJpaRepository, never()).save(any());
        verify(userJpaRepository, never()).saveAndFlush(any());
        verify(userJpaRepository, times(1)).nextUserId();
        verify(pendingAuthStore, times(1)).claimUserId(OauthType.GOOGLE.getNumber(), "google-sub-123", 100L);
        // save 는 본체 키와 oauth 인덱스 키를 모두 기록한다 (PendingAuthStoreSaveTest 가 고정).
        // 여기서는 두 키를 조립할 재료가 프로필에 다 담겨 나가는지만 본다.
        verify(pendingAuthStore, times(1)).save(argThat(profile ->
                profile.getUserId().equals(100L)
                        && profile.getIdentifyId().equals("google-sub-123")
                        && profile.getOauthType() == OauthType.GOOGLE.getNumber()
                        && profile.getEmail().equals("test@gmail.com")));
    }

    @Test
    void authorize_기존_ACTIVE_유저이면_pending_세션을_만들지_않고_DB_값으로_응답한다() {
        UserEntity existing = UserEntity.from(new User(2L, "test@gmail.com", "홍길동", "00001", null,
                "google-sub-123", OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber()));
        when(googleOauthPort.authorize("id-token")).thenReturn(pendingProfile());
        when(userJpaRepository.findByOauthTypeAndIdentifyId(OauthType.GOOGLE.getNumber(), "google-sub-123"))
                .thenReturn(Optional.of(existing));
        when(jwtTokenProvider.generateToken(2L, "device-1")).thenReturn("jwt-token");

        AuthorizeResponse response = authService.authorize("id-token", "device-1", OauthType.GOOGLE);

        assertThat(response.isPending()).isFalse();
        assertThat(response.pendingProfile()).isNull();
        assertThat(response.user().getId()).isEqualTo(2L);
        // 오늘은 어댑터가 만든 name/tag/PENDING 이 그대로 나갔다 (이슈 #25). 이제 DB 값이 나간다.
        assertThat(response.user().getName()).isEqualTo("홍길동");
        assertThat(response.user().getTag()).isEqualTo("00001");
        assertThat(response.user().getStatus()).isEqualTo(UserStatus.ACTIVE.getNumber());
        assertThat(response.jwtToken()).isEqualTo("jwt-token");
        verify(userJpaRepository, never()).nextUserId();
        verify(pendingAuthStore, never()).findUserId(anyInt(), any());
        verify(pendingAuthStore, never()).claimUserId(anyInt(), any(), any());
        verify(pendingAuthStore, never()).save(any());
        verify(userJpaRepository, never()).save(any());
    }

    @Test
    void authorize_TTL내_재호출이면_기존_pending_userId를_재사용한다() {
        when(googleOauthPort.authorize("id-token")).thenReturn(pendingProfile());
        when(userJpaRepository.findByOauthTypeAndIdentifyId(anyInt(), any())).thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(OauthType.GOOGLE.getNumber(), "google-sub-123"))
                .thenReturn(Optional.of(100L));
        when(jwtTokenProvider.generateToken(100L, "device-2")).thenReturn("jwt-token-2");

        AuthorizeResponse response = authService.authorize("id-token", "device-2", OauthType.GOOGLE);

        // 새 id 를 뽑으면 선행 호출로 발급한 토큰이 무효해진다.
        verify(userJpaRepository, never()).nextUserId();
        verify(pendingAuthStore, never()).claimUserId(anyInt(), any(), any());
        assertThat(response.pendingProfile().getUserId()).isEqualTo(100L);
        assertThat(response.jwtToken()).isEqualTo("jwt-token-2");
    }

    @Test
    void authorize_동시요청에_선점_패배하면_승자의_userId를_따른다() {
        when(googleOauthPort.authorize("id-token")).thenReturn(pendingProfile());
        when(userJpaRepository.findByOauthTypeAndIdentifyId(anyInt(), any())).thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(OauthType.GOOGLE.getNumber(), "google-sub-123"))
                .thenReturn(Optional.empty(), Optional.of(100L));
        when(userJpaRepository.nextUserId()).thenReturn(101L);
        when(pendingAuthStore.claimUserId(OauthType.GOOGLE.getNumber(), "google-sub-123", 101L))
                .thenReturn(false);
        when(jwtTokenProvider.generateToken(100L, "device-1")).thenReturn("jwt-token");

        AuthorizeResponse response = authService.authorize("id-token", "device-1", OauthType.GOOGLE);

        // 버려진 101 은 시퀀스 gap 으로 남는다. 승자의 100 을 따라야 두 토큰이 같은 세션을 본다.
        assertThat(response.pendingProfile().getUserId()).isEqualTo(100L);
        assertThat(response.jwtToken()).isEqualTo("jwt-token");
        verify(pendingAuthStore).save(argThat(profile -> profile.getUserId().equals(100L)));
    }

    @Test
    void authorize_시퀀스를_찾을_수_없으면_IllegalStateException() {
        when(googleOauthPort.authorize("id-token")).thenReturn(pendingProfile());
        when(userJpaRepository.findByOauthTypeAndIdentifyId(anyInt(), any())).thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(anyInt(), any())).thenReturn(Optional.empty());
        when(userJpaRepository.nextUserId()).thenReturn(null);

        assertThatThrownBy(() -> authService.authorize("id-token", "device-1", OauthType.GOOGLE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pg_get_serial_sequence");
    }

    @Test
    void authorize_기존유저의_profileImageUrl은_public_URL로_치환된다() {
        // 기존 회귀 가드. 단 이제 소스가 어댑터가 만든 User 가 아니라 DB 엔티티다.
        User storedUser = new User(2L, "test@gmail.com", "홍길동", "00001", null,
                "google-sub-123", OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber());
        storedUser.setProfileImageUrl("profiles/2/x.webp");
        UserEntity existing = UserEntity.from(storedUser);

        when(googleOauthPort.authorize("id-token")).thenReturn(pendingProfile());
        when(userJpaRepository.findByOauthTypeAndIdentifyId(OauthType.GOOGLE.getNumber(), "google-sub-123"))
                .thenReturn(Optional.of(existing));
        when(jwtTokenProvider.generateToken(2L, "device-1")).thenReturn("jwt-token");
        when(r2Uploader.toPublicUrl("profiles/2/x.webp"))
                .thenReturn("https://cdn.example.com/2/x");

        AuthorizeResponse response = authService.authorize("id-token", "device-1", OauthType.GOOGLE);

        assertThat(response.user().getProfileImageUrl()).isEqualTo("https://cdn.example.com/2/x");
        verify(r2Uploader).toPublicUrl("profiles/2/x.webp");
    }
}
