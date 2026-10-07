package com.danmalgi.backend.auth.service;

import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException;
import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.exception.UnsupportedOauthTypeException;
import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.grpc.dto.AuthorizeResponse;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthAuthorizationCodeExchangePort;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.auth.repository.entity.UserOAuthIdentityEntity;
import com.danmalgi.backend.auth.repository.persistence.UserAppleCredentialJpaRepository;
import com.danmalgi.backend.auth.repository.persistence.UserOAuthIdentityJpaRepository;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.device.service.DeviceService;
import com.danmalgi.backend.global.infrastructure.crypto.CredentialCipher;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private CredentialCipher credentialCipher;

    @Mock
    private UserAppleCredentialJpaRepository userAppleCredentialJpaRepository;

    @Mock
    private UserOAuthIdentityJpaRepository userOAuthIdentityJpaRepository;

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
                pendingAuthStore,
                List.of(),
                credentialCipher,
                userAppleCredentialJpaRepository,
                userOAuthIdentityJpaRepository
        );
    }

    // id 는 IDENTITY 로 DB 가 채우는 값이라 테스트에서는 직접 넣는다.
    private static UserOAuthIdentityEntity identity(Long id, UserEntity user, OauthType provider, String subject) {
        UserOAuthIdentityEntity identity = UserOAuthIdentityEntity.link(user, provider, subject, user.getEmail(), Instant.EPOCH);
        ReflectionTestUtils.setField(identity, "id", id);
        return identity;
    }

    private PendingOAuthProfile pendingProfile() {
        return new PendingOAuthProfile(
                null, "test@gmail.com", "google-sub-123", OauthType.GOOGLE, null, null, null);
    }

    @Test
    void authorize_지원하지_않는_OauthType이면_예외발생() {
        assertThatThrownBy(() -> authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.APPLE))
                .isInstanceOf(UnsupportedOauthTypeException.class);
    }

    @Test
    void authorize_OAuthPort가_null_반환하면_예외발생() {
        when(googleOauthPort.authorize(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE))
                .isInstanceOf(OauthAuthorizeFailException.class);
    }

    @Test
    void authorize_credential을_그대로_포트에_전달한다() {
        // rawNonce 검증은 어댑터 책임이다. service 가 값을 빠뜨리거나 바꾸면 검증이 조용히 꺼진다.
        OAuthCredential credential = new OAuthCredential("id-token", "raw-nonce", null);
        when(googleOauthPort.authorize(credential)).thenReturn(null);

        assertThatThrownBy(() -> authService.authorize(credential, "device-1", OauthType.GOOGLE))
                .isInstanceOf(OauthAuthorizeFailException.class);

        verify(googleOauthPort).authorize(credential);
    }

    @Test
    void authorize_신규계정이면_users_행을_저장하지_않고_pending_세션만_만든다() {
        when(googleOauthPort.authorize(new OAuthCredential("id-token", null, null))).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(OauthType.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userJpaRepository.nextUserId()).thenReturn(100L);
        when(pendingAuthStore.claimUserId(OauthType.GOOGLE, "google-sub-123", 100L))
                .thenReturn(true);
        when(jwtTokenProvider.generateToken(100L, "device-1")).thenReturn("jwt-token");

        AuthorizeResponse response = authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);

        assertThat(response.isPending()).isTrue();
        assertThat(response.user()).isNull();
        assertThat(response.pendingProfile().getUserId()).isEqualTo(100L);
        assertThat(response.jwtToken()).isEqualTo("jwt-token");
        verify(userJpaRepository, never()).save(any());
        verify(userJpaRepository, never()).saveAndFlush(any());
        verify(userJpaRepository, times(1)).nextUserId();
        verify(pendingAuthStore, times(1)).claimUserId(OauthType.GOOGLE, "google-sub-123", 100L);
        // save 는 본체 키와 oauth 인덱스 키를 모두 기록한다 (PendingAuthStoreSaveTest 가 고정).
        // 여기서는 두 키를 조립할 재료가 프로필에 다 담겨 나가는지만 본다.
        verify(pendingAuthStore, times(1)).save(argThat(profile ->
                profile.getUserId().equals(100L)
                        && profile.getIdentifyId().equals("google-sub-123")
                        && profile.getOauthType() == OauthType.GOOGLE
                        && profile.getEmail().equals("test@gmail.com")));
    }

    @Test
    void authorize_기존_ACTIVE_유저이면_pending_세션을_만들지_않고_DB_값으로_응답한다() {
        UserEntity existing = UserEntity.from(new User(2L, "test@gmail.com", "홍길동", "00001", null, UserStatus.ACTIVE.getNumber()));
        when(googleOauthPort.authorize(new OAuthCredential("id-token", null, null))).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(identity(9L, existing, OauthType.GOOGLE, "google-sub-123")));
        when(jwtTokenProvider.generateToken(2L, "device-1")).thenReturn("jwt-token");

        AuthorizeResponse response = authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);

        assertThat(response.isPending()).isFalse();
        assertThat(response.pendingProfile()).isNull();
        assertThat(response.user().getId()).isEqualTo(2L);
        // 오늘은 어댑터가 만든 name/tag/PENDING 이 그대로 나갔다 (이슈 #25). 이제 DB 값이 나간다.
        assertThat(response.user().getName()).isEqualTo("홍길동");
        assertThat(response.user().getTag()).isEqualTo("00001");
        assertThat(response.user().getStatus()).isEqualTo(UserStatus.ACTIVE.getNumber());
        assertThat(response.jwtToken()).isEqualTo("jwt-token");
        verify(userJpaRepository, never()).nextUserId();
        verify(pendingAuthStore, never()).findUserId(any(), any());
        verify(pendingAuthStore, never()).claimUserId(any(), any(), any());
        verify(pendingAuthStore, never()).save(any());
        verify(userJpaRepository, never()).save(any());
    }

    @Test
    void authorize_기존유저면_신원의_마지막_인증시각과_제공자_email만_갱신한다() {
        UserEntity existing = UserEntity.from(new User(2L, "first@gmail.com", "홍길동", "00001", null, UserStatus.ACTIVE.getNumber()));
        when(googleOauthPort.authorize(any())).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(identity(9L, existing, OauthType.GOOGLE, "google-sub-123")));

        AuthorizeResponse response = authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);

        verify(userOAuthIdentityJpaRepository).touchAuthenticated(eq(9L), eq("test@gmail.com"), any(Instant.class));
        // users.email(대표 이메일)은 가입 때 값을 유지한다. 연동이 생기면 고르는 규칙이 따로 필요하다.
        assertThat(response.user().getEmail()).isEqualTo("first@gmail.com");
        verify(userJpaRepository, never()).save(any());
        verify(userJpaRepository, never()).saveAndFlush(any());
    }

    @Test
    void authorize_신규계정이면_신원을_갱신하지_않는다() {
        when(googleOauthPort.authorize(any())).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(any(), any())).thenReturn(Optional.of(100L));

        authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);

        verify(userOAuthIdentityJpaRepository, never()).touchAuthenticated(any(), any(), any());
    }

    @Test
    void authorize_TTL내_재호출이면_기존_pending_userId를_재사용한다() {
        when(googleOauthPort.authorize(new OAuthCredential("id-token", null, null))).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(OauthType.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(100L));
        when(jwtTokenProvider.generateToken(100L, "device-2")).thenReturn("jwt-token-2");

        AuthorizeResponse response = authService.authorize(new OAuthCredential("id-token", null, null), "device-2", OauthType.GOOGLE);

        // 새 id 를 뽑으면 선행 호출로 발급한 토큰이 무효해진다.
        verify(userJpaRepository, never()).nextUserId();
        verify(pendingAuthStore, never()).claimUserId(any(), any(), any());
        assertThat(response.pendingProfile().getUserId()).isEqualTo(100L);
        assertThat(response.jwtToken()).isEqualTo("jwt-token-2");
    }

    @Test
    void authorize_동시요청에_선점_패배하면_승자의_userId를_따른다() {
        when(googleOauthPort.authorize(new OAuthCredential("id-token", null, null))).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(OauthType.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty(), Optional.of(100L));
        when(userJpaRepository.nextUserId()).thenReturn(101L);
        when(pendingAuthStore.claimUserId(OauthType.GOOGLE, "google-sub-123", 101L))
                .thenReturn(false);
        when(jwtTokenProvider.generateToken(100L, "device-1")).thenReturn("jwt-token");

        AuthorizeResponse response = authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);

        // 버려진 101 은 시퀀스 gap 으로 남는다. 승자의 100 을 따라야 두 토큰이 같은 세션을 본다.
        assertThat(response.pendingProfile().getUserId()).isEqualTo(100L);
        assertThat(response.jwtToken()).isEqualTo("jwt-token");
        verify(pendingAuthStore).save(argThat(profile -> profile.getUserId().equals(100L)));
    }

    @Test
    void authorize_시퀀스를_찾을_수_없으면_IllegalStateException() {
        when(googleOauthPort.authorize(new OAuthCredential("id-token", null, null))).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(any(), any())).thenReturn(Optional.empty());
        when(userJpaRepository.nextUserId()).thenReturn(null);

        assertThatThrownBy(() -> authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pg_get_serial_sequence");
    }

    @Test
    void authorize_기존유저의_profileImageUrl은_public_URL로_치환된다() {
        // 기존 회귀 가드. 단 이제 소스가 어댑터가 만든 User 가 아니라 DB 엔티티다.
        User storedUser = new User(2L, "test@gmail.com", "홍길동", "00001", null, UserStatus.ACTIVE.getNumber());
        storedUser.setProfileImageUrl("profiles/2/x.webp");
        UserEntity existing = UserEntity.from(storedUser);

        when(googleOauthPort.authorize(new OAuthCredential("id-token", null, null))).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(identity(9L, existing, OauthType.GOOGLE, "google-sub-123")));
        when(jwtTokenProvider.generateToken(2L, "device-1")).thenReturn("jwt-token");
        when(r2Uploader.toPublicUrl("profiles/2/x.webp"))
                .thenReturn("https://cdn.example.com/2/x");

        AuthorizeResponse response = authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);

        assertThat(response.user().getProfileImageUrl()).isEqualTo("https://cdn.example.com/2/x");
        verify(r2Uploader).toPublicUrl("profiles/2/x.webp");
    }

    @Test
    void authorize_APPLE은_같은_email의_GOOGLE_계정과_연결하지_않고_APPLE_sub로만_조회한다() {
        OAuthPlatformAuthorizationPort appleOauthPort = mock(OAuthPlatformAuthorizationPort.class);
        when(appleOauthPort.supportedOauthType()).thenReturn(OauthType.APPLE);
        AuthService service = new AuthService(userJpaRepository, deviceJpaRepository, deviceService,
                jwtTokenProvider, List.of(googleOauthPort, appleOauthPort), r2Uploader, pendingAuthStore,
                List.of(), credentialCipher, userAppleCredentialJpaRepository, userOAuthIdentityJpaRepository);
        when(appleOauthPort.authorize(new OAuthCredential("apple-id-token", null, null))).thenReturn(new PendingOAuthProfile(
                null, "test@gmail.com", "apple-sub-001", OauthType.APPLE, null, null, null));
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001"))
                .thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(OauthType.APPLE, "apple-sub-001"))
                .thenReturn(Optional.empty());
        when(userJpaRepository.nextUserId()).thenReturn(200L);
        when(pendingAuthStore.claimUserId(OauthType.APPLE, "apple-sub-001", 200L)).thenReturn(true);
        when(jwtTokenProvider.generateToken(200L, "device-1")).thenReturn("jwt-token");

        AuthorizeResponse response = service.authorize(new OAuthCredential("apple-id-token", null, null), "device-1", OauthType.APPLE);

        assertThat(response.isPending()).isTrue();
        assertThat(response.pendingProfile().getUserId()).isEqualTo(200L);
        verify(googleOauthPort, never()).authorize(any());
        verify(userOAuthIdentityJpaRepository).findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001");
        verify(userOAuthIdentityJpaRepository, times(1)).findByProviderAndProviderSubject(any(), any());
    }

    // ---- Apple authorization code 교환 ----

    private static final String APPLE_CLIENT_ID = "com.danmalgi.mobile";
    private static final OAuthCredential APPLE_CREDENTIAL = new OAuthCredential("apple-id-token", null, "auth-code");

    private PendingOAuthProfile applePendingProfile() {
        return PendingOAuthProfile.builder()
                .email("abc@privaterelay.appleid.com")
                .identifyId("apple-sub-001")
                .oauthType(OauthType.APPLE)
                .oauthClientId(APPLE_CLIENT_ID)
                .build();
    }

    private AuthService appleService(OAuthAuthorizationCodeExchangePort codeExchangePort) {
        OAuthPlatformAuthorizationPort appleOauthPort = mock(OAuthPlatformAuthorizationPort.class);
        when(appleOauthPort.supportedOauthType()).thenReturn(OauthType.APPLE);
        lenient().when(appleOauthPort.authorize(any())).thenReturn(applePendingProfile());
        when(codeExchangePort.supportedOauthType()).thenReturn(OauthType.APPLE);
        return new AuthService(userJpaRepository, deviceJpaRepository, deviceService, jwtTokenProvider,
                List.of(googleOauthPort, appleOauthPort), r2Uploader, pendingAuthStore,
                List.of(codeExchangePort), credentialCipher, userAppleCredentialJpaRepository, userOAuthIdentityJpaRepository);
    }

    private void givenNewAppleUser(Long userId) {
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001"))
                .thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(OauthType.APPLE, "apple-sub-001")).thenReturn(Optional.empty());
        when(userJpaRepository.nextUserId()).thenReturn(userId);
        when(pendingAuthStore.claimUserId(OauthType.APPLE, "apple-sub-001", userId)).thenReturn(true);
        when(jwtTokenProvider.generateToken(userId, "device-1")).thenReturn("jwt-token");
    }

    private void givenExistingAppleUser(Long userId) {
        UserEntity existing = UserEntity.from(new User(userId, "abc@privaterelay.appleid.com", "홍길동", "00001", null, UserStatus.ACTIVE.getNumber()));
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001"))
                .thenReturn(Optional.of(identity(30L, existing, OauthType.APPLE, "apple-sub-001")));
        when(jwtTokenProvider.generateToken(userId, "device-1")).thenReturn("jwt-token");
    }

    @Test
    void authorize_APPLE_신규유저면_교환한_refresh_token을_암호화해_pending에_담는다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        givenNewAppleUser(200L);
        when(codeExchangePort.exchange("auth-code", APPLE_CLIENT_ID)).thenReturn("apple-refresh-token");
        when(credentialCipher.encrypt("apple-refresh-token")).thenReturn("v1:encrypted");

        AuthorizeResponse response = service.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE);

        assertThat(response.isPending()).isTrue();
        verify(pendingAuthStore).save(argThat(profile ->
                profile.getUserId().equals(200L)
                        && "v1:encrypted".equals(profile.getEncryptedRefreshToken())
                        && APPLE_CLIENT_ID.equals(profile.getOauthClientId())));
        // users 행이 아직 없으므로 DB 저장은 Register 에서 한다.
        verify(userAppleCredentialJpaRepository, never()).upsert(any(), any(), any());
    }

    @Test
    void authorize_APPLE_신규유저가_교환에_실패하면_pending을_만들지_않고_거절한다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001"))
                .thenReturn(Optional.empty());
        when(codeExchangePort.exchange("auth-code", APPLE_CLIENT_ID)).thenThrow(
                new AuthorizationCodeExchangeException(AuthorizationCodeExchangeException.Reason.UNAVAILABLE, "down"));

        assertThatThrownBy(() -> service.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE))
                .isInstanceOfSatisfying(AuthorizationCodeExchangeException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(Status.UNAVAILABLE));

        // 토큰 없이 가입하면 revoke 할 수 없다. 시퀀스와 pending 세션도 쓰지 않는다.
        verify(userJpaRepository, never()).nextUserId();
        verify(pendingAuthStore, never()).save(any());
    }

    @Test
    void authorize_APPLE_신규유저인데_authorization_code가_없으면_UNAUTHENTICATED() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(
                new OAuthCredential("apple-id-token", null, null), "device-1", OauthType.APPLE))
                .isInstanceOfSatisfying(AuthorizationCodeExchangeException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(Status.UNAUTHENTICATED));

        verify(codeExchangePort, never()).exchange(any(), any());
        verify(pendingAuthStore, never()).save(any());
    }

    @Test
    void authorize_APPLE_기존유저면_refresh_token을_갱신한다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        givenExistingAppleUser(3L);
        when(codeExchangePort.exchange("auth-code", APPLE_CLIENT_ID)).thenReturn("apple-refresh-token");
        when(credentialCipher.encrypt("apple-refresh-token")).thenReturn("v1:encrypted");

        AuthorizeResponse response = service.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE);

        assertThat(response.isPending()).isFalse();
        assertThat(response.user().getId()).isEqualTo(3L);
        verify(userAppleCredentialJpaRepository).upsert(30L, APPLE_CLIENT_ID, "v1:encrypted");
    }

    @Test
    void authorize_APPLE_기존유저는_교환에_실패해도_로그인한다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        givenExistingAppleUser(3L);
        when(codeExchangePort.exchange("auth-code", APPLE_CLIENT_ID)).thenThrow(
                new AuthorizationCodeExchangeException(AuthorizationCodeExchangeException.Reason.REJECTED, "invalid_grant"));

        AuthorizeResponse response = service.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE);

        assertThat(response.user().getId()).isEqualTo(3L);
        assertThat(response.jwtToken()).isEqualTo("jwt-token");
        verify(userAppleCredentialJpaRepository, never()).upsert(any(), any(), any());
    }

    @Test
    void authorize_APPLE_기존유저는_authorization_code가_없어도_로그인한다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        givenExistingAppleUser(3L);

        AuthorizeResponse response = service.authorize(
                new OAuthCredential("apple-id-token", null, null), "device-1", OauthType.APPLE);

        assertThat(response.user().getId()).isEqualTo(3L);
        verify(codeExchangePort, never()).exchange(any(), any());
    }

    @Test
    void authorize_GOOGLE이면_교환하지_않는다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        when(googleOauthPort.authorize(any())).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(pendingAuthStore.findUserId(any(), any())).thenReturn(Optional.of(100L));

        service.authorize(new OAuthCredential("id-token", null, "auth-code"), "device-1", OauthType.GOOGLE);

        verify(codeExchangePort, never()).exchange(any(), any());
        verify(credentialCipher, never()).encrypt(any());
    }
}
