package com.danmalgi.backend.auth.service;

import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException;
import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.exception.OauthProviderUnavailableException;
import com.danmalgi.backend.auth.domain.exception.UnsupportedOauthTypeException;
import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.grpc.dto.AuthorizeResponse;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthAuthorizationCodeExchangePort;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.auth.repository.entity.UserOAuthIdentityEntity;
import com.danmalgi.backend.auth.repository.persistence.UserOAuthCredentialJpaRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceAuthorizeTest {

    private static final String UUID_PATTERN =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

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
    private UserOAuthCredentialJpaRepository userOAuthCredentialJpaRepository;

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
                userOAuthCredentialJpaRepository,
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
                "test@gmail.com", "google-sub-123", OauthType.GOOGLE, null, null, null);
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
    void authorize_신규계정이면_users행을_저장하지_않고_UUID_pending_세션만_만든다() {
        when(googleOauthPort.authorize(new OAuthCredential("id-token", null, null))).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(jwtTokenProvider.generatePendingToken(anyString(), eq("device-1"))).thenReturn("jwt-token");

        AuthorizeResponse response = authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);

        assertThat(response.isPending()).isTrue();
        assertThat(response.user()).isNull();
        assertThat(response.jwtToken()).isEqualTo("jwt-token");
        verify(userJpaRepository, never()).save(any());
        verify(userJpaRepository, never()).saveAndFlush(any());

        ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(pendingAuthStore).save(sessionIdCaptor.capture(), argThat(profile ->
                profile.getIdentifyId().equals("google-sub-123")
                        && profile.getOauthType() == OauthType.GOOGLE
                        && profile.getEmail().equals("test@gmail.com")));
        assertThat(sessionIdCaptor.getValue()).matches(UUID_PATTERN);
    }

    @Test
    void authorize_신규계정이면_pendingSessionId_기반_토큰을_발급하고_userId_토큰은_발급하지_않는다() {
        when(googleOauthPort.authorize(new OAuthCredential("id-token", null, null))).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(jwtTokenProvider.generatePendingToken(anyString(), eq("device-1"))).thenReturn("jwt-token");

        AuthorizeResponse response = authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);

        assertThat(response.jwtToken()).isEqualTo("jwt-token");
        verify(jwtTokenProvider).generatePendingToken(anyString(), eq("device-1"));
        verify(jwtTokenProvider, never()).generateToken(any(), any());
    }

    @Test
    void authorize_같은_계정으로_두번_호출하면_서로_다른_세션이_만들어지고_둘_다_유효하다() {
        // 사전 채번 방식의 "선점 경합"/"TTL 내 재사용" 문제가 세션을 UUID 로 분리하면서 사라졌다 —
        // 각 Authorization 호출은 독립된 세션 + 독립된 토큰을 받고, 둘 다 그대로 유효하다.
        when(googleOauthPort.authorize(new OAuthCredential("id-token", null, null))).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(jwtTokenProvider.generatePendingToken(anyString(), eq("device-1")))
                .thenReturn("jwt-token-1", "jwt-token-2");

        AuthorizeResponse first = authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);
        AuthorizeResponse second = authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);

        assertThat(first.jwtToken()).isEqualTo("jwt-token-1");
        assertThat(second.jwtToken()).isEqualTo("jwt-token-2");

        ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(pendingAuthStore, times(2)).save(sessionIdCaptor.capture(), any());
        List<String> sessionIds = sessionIdCaptor.getAllValues();
        assertThat(sessionIds.get(0)).isNotEqualTo(sessionIds.get(1));
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
        verify(pendingAuthStore, never()).save(any(), any());
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

        authService.authorize(new OAuthCredential("id-token", null, null), "device-1", OauthType.GOOGLE);

        verify(userOAuthIdentityJpaRepository, never()).touchAuthenticated(any(), any(), any());
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
                List.of(), credentialCipher, userOAuthCredentialJpaRepository, userOAuthIdentityJpaRepository);
        when(appleOauthPort.authorize(new OAuthCredential("apple-id-token", null, null))).thenReturn(new PendingOAuthProfile(
                "test@gmail.com", "apple-sub-001", OauthType.APPLE, null, null, null));
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001"))
                .thenReturn(Optional.empty());
        when(jwtTokenProvider.generatePendingToken(anyString(), eq("device-1"))).thenReturn("jwt-token");

        AuthorizeResponse response = service.authorize(new OAuthCredential("apple-id-token", null, null), "device-1", OauthType.APPLE);

        assertThat(response.isPending()).isTrue();
        verify(googleOauthPort, never()).authorize(any());
        verify(userOAuthIdentityJpaRepository).findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001");
        verify(userOAuthIdentityJpaRepository, times(1)).findByProviderAndProviderSubject(any(), any());
    }

    // ---- Apple authorization code 교환 ----

    private static final String APPLE_CLIENT_ID = "com.danmalgi.mobile";
    private static final String APPLE_SUB = "apple-sub-001";
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
                List.of(codeExchangePort), credentialCipher, userOAuthCredentialJpaRepository, userOAuthIdentityJpaRepository);
    }

    private void givenNewAppleUser() {
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001"))
                .thenReturn(Optional.empty());
        lenient().when(jwtTokenProvider.generatePendingToken(anyString(), eq("device-1"))).thenReturn("jwt-token");
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
        givenNewAppleUser();
        when(codeExchangePort.exchange("auth-code", APPLE_CLIENT_ID, APPLE_SUB)).thenReturn("apple-refresh-token");
        when(credentialCipher.encrypt("apple-refresh-token")).thenReturn("v1:encrypted");

        AuthorizeResponse response = service.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE);

        assertThat(response.isPending()).isTrue();
        verify(pendingAuthStore).save(any(), argThat(profile ->
                "v1:encrypted".equals(profile.getEncryptedRefreshToken())
                        && APPLE_CLIENT_ID.equals(profile.getOauthClientId())));
        // users 행이 아직 없으므로 DB 저장은 Register 에서 한다.
        verify(userOAuthCredentialJpaRepository, never()).upsert(any(), any(), any());
    }

    @Test
    void authorize_APPLE_신규유저가_교환에_실패하면_pending을_만들지_않고_거절한다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001"))
                .thenReturn(Optional.empty());
        when(codeExchangePort.exchange("auth-code", APPLE_CLIENT_ID, APPLE_SUB)).thenThrow(
                new AuthorizationCodeExchangeException(AuthorizationCodeExchangeException.Reason.UNAVAILABLE, "down"));

        assertThatThrownBy(() -> service.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE))
                .isInstanceOfSatisfying(AuthorizationCodeExchangeException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(Status.UNAVAILABLE));

        // 토큰 없이 가입하면 revoke 할 수 없다. pending 세션도 만들지 않는다.
        verify(pendingAuthStore, never()).save(any(), any());
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

        verify(codeExchangePort, never()).exchange(any(), any(), any());
        verify(pendingAuthStore, never()).save(any(), any());
    }

    @Test
    void authorize_APPLE_기존유저면_refresh_token을_갱신한다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        givenExistingAppleUser(3L);
        when(codeExchangePort.exchange("auth-code", APPLE_CLIENT_ID, APPLE_SUB)).thenReturn("apple-refresh-token");
        when(credentialCipher.encrypt("apple-refresh-token")).thenReturn("v1:encrypted");

        AuthorizeResponse response = service.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE);

        assertThat(response.isPending()).isFalse();
        assertThat(response.user().getId()).isEqualTo(3L);
        verify(userOAuthCredentialJpaRepository).upsert(30L, APPLE_CLIENT_ID, "v1:encrypted");
    }

    @Test
    void authorize_APPLE_기존유저는_교환에_실패해도_로그인한다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        givenExistingAppleUser(3L);
        when(codeExchangePort.exchange("auth-code", APPLE_CLIENT_ID, APPLE_SUB)).thenThrow(
                new AuthorizationCodeExchangeException(AuthorizationCodeExchangeException.Reason.REJECTED, "invalid_grant"));

        AuthorizeResponse response = service.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE);

        assertThat(response.user().getId()).isEqualTo(3L);
        assertThat(response.jwtToken()).isEqualTo("jwt-token");
        verify(userOAuthCredentialJpaRepository, never()).upsert(any(), any(), any());
    }

    @Test
    void authorize_APPLE_교환에_검증된_idToken의_sub를_넘긴다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        givenExistingAppleUser(3L);
        when(codeExchangePort.exchange(any(), any(), any())).thenReturn("apple-refresh-token");
        when(credentialCipher.encrypt("apple-refresh-token")).thenReturn("v1:encrypted");

        service.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE);

        // 교환 응답의 id_token 이 같은 계정인지 어댑터가 대조할 수 있어야 한다.
        verify(codeExchangePort).exchange("auth-code", APPLE_CLIENT_ID, "apple-sub-001");
    }

    @Test
    void authorize_APPLE_기존유저라도_code가_다른_계정의_것이면_로그인을_막는다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(OauthType.APPLE, "apple-sub-001"))
                .thenReturn(Optional.of(identity(30L,
                        UserEntity.from(new User(3L, "abc@privaterelay.appleid.com", "홍길동", "00001", null,
                                UserStatus.ACTIVE.getNumber())),
                        OauthType.APPLE, "apple-sub-001")));
        when(codeExchangePort.exchange("auth-code", APPLE_CLIENT_ID, APPLE_SUB)).thenThrow(
                new AuthorizationCodeExchangeException(AuthorizationCodeExchangeException.Reason.SUBJECT_MISMATCH, "subject mismatch"));

        assertThatThrownBy(() -> service.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE))
                .isInstanceOfSatisfying(AuthorizationCodeExchangeException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(Status.UNAUTHENTICATED));

        verify(jwtTokenProvider, never()).generateToken(any(), any());
        verify(userOAuthCredentialJpaRepository, never()).upsert(any(), any(), any());
    }

    @Test
    void authorize_APPLE_제공자_장애_예외는_그대로_전파한다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        when(codeExchangePort.supportedOauthType()).thenReturn(OauthType.APPLE);
        OAuthPlatformAuthorizationPort appleOauthPort = mock(OAuthPlatformAuthorizationPort.class);
        when(appleOauthPort.supportedOauthType()).thenReturn(OauthType.APPLE);
        when(appleOauthPort.authorize(any())).thenThrow(new OauthProviderUnavailableException("JWKS down", null));
        AuthService unavailableService = new AuthService(userJpaRepository, deviceJpaRepository, deviceService,
                jwtTokenProvider, List.of(googleOauthPort, appleOauthPort), r2Uploader, pendingAuthStore,
                List.of(codeExchangePort), credentialCipher, userOAuthCredentialJpaRepository, userOAuthIdentityJpaRepository);

        // 재로그인이 아니라 재시도로 대응하도록 UNAUTHENTICATED 로 바꾸지 않는다.
        assertThatThrownBy(() -> unavailableService.authorize(APPLE_CREDENTIAL, "device-1", OauthType.APPLE))
                .isInstanceOfSatisfying(OauthProviderUnavailableException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(Status.UNAVAILABLE));
        verify(codeExchangePort, never()).exchange(any(), any(), any());
    }

    @Test
    void authorize_APPLE_기존유저는_authorization_code가_없어도_로그인한다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        givenExistingAppleUser(3L);

        AuthorizeResponse response = service.authorize(
                new OAuthCredential("apple-id-token", null, null), "device-1", OauthType.APPLE);

        assertThat(response.user().getId()).isEqualTo(3L);
        verify(codeExchangePort, never()).exchange(any(), any(), any());
    }

    @Test
    void authorize_GOOGLE이면_교환하지_않는다() {
        OAuthAuthorizationCodeExchangePort codeExchangePort = mock(OAuthAuthorizationCodeExchangePort.class);
        AuthService service = appleService(codeExchangePort);
        when(googleOauthPort.authorize(any())).thenReturn(pendingProfile());
        when(userOAuthIdentityJpaRepository.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());

        service.authorize(new OAuthCredential("id-token", null, "auth-code"), "device-1", OauthType.GOOGLE);

        verify(codeExchangePort, never()).exchange(any(), any(), any());
        verify(credentialCipher, never()).encrypt(any());
    }
}
