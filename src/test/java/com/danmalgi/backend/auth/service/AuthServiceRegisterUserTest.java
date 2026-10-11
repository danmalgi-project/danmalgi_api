package com.danmalgi.backend.auth.service;

import com.danmalgi.backend.auth.domain.exception.AlreadyRegisteredException;
import com.danmalgi.backend.auth.domain.exception.PendingRegistrationNotFoundException;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
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
import com.danmalgi.backend.user.domain.exception.DuplicatedUserException;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterUserTest {

    private static final Long IDENTITY_ID = 50L;
    private static final String SESSION_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private DeviceJpaRepository deviceJpaRepository;

    @Mock
    private DeviceService deviceService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private OAuthPlatformAuthorizationPort oauthPort;

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
        when(oauthPort.supportedOauthType()).thenReturn(OauthType.GOOGLE);
        authService = new AuthService(
                userJpaRepository,
                deviceJpaRepository,
                deviceService,
                jwtTokenProvider,
                List.of(oauthPort),
                r2Uploader,
                pendingAuthStore,
                List.of(),
                credentialCipher,
                userOAuthCredentialJpaRepository,
                userOAuthIdentityJpaRepository
        );
        // 신원 id 는 IDENTITY 로 DB 가 채운다. 저장된 것처럼 id 를 넣어 돌려준다.
        lenient().when(userOAuthIdentityJpaRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            UserOAuthIdentityEntity identity = invocation.getArgument(0);
            ReflectionTestUtils.setField(identity, "id", IDENTITY_ID);
            return identity;
        });
        // users.id 도 IDENTITY 다. saveAndFlush 가 DB 채번을 흉내 내 100 을 채워 돌려준다.
        // entity 가 null 일 수 있다 — 테스트가 이 스텁을 when(...).thenThrow(...) 로 재정의할 때
        // Mockito 가 기존 답변을 한 번 재생하며 any() 자리에 null 을 넘긴다.
        lenient().when(userJpaRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            if (entity != null) {
                ReflectionTestUtils.setField(entity, "id", 100L);
            }
            return entity;
        });
    }

    /** Authorization 단계에서 만든 UUID pending 세션. 해당 users 행은 아직 없다. */
    private PendingOAuthProfile pendingProfile() {
        return new PendingOAuthProfile(
                "test@gmail.com", "google-sub-123", OauthType.GOOGLE, null, null, null);
    }

    private UserEntity registeredUser() {
        return UserEntity.from(new User(100L, "test@gmail.com", "홍길동", "00001", null, UserStatus.ACTIVE.getNumber()));
    }

    @Test
    void registerUser_세션id가_null이면_AlreadyRegisteredException() {
        // 등록 완료 토큰(userId 클레임)으로 Register 를 부른 경우다. 인터셉터가 pending 토큰에만
        // PENDING_SESSION_ID 를 심으므로 정상 클라이언트는 이 경로를 타지 않는다.
        assertThatThrownBy(() -> authService.registerUser(null, "홍길동", "00001"))
                .isInstanceOf(AlreadyRegisteredException.class);

        verify(pendingAuthStore, never()).find(any());
    }

    @Test
    void registerUser_pending세션이_있으면_ACTIVE로_INSERT하고_완료마커를_남긴다() {
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());

        User result = authService.registerUser(SESSION_ID, "  홍길동  ", "  00001  ");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getName()).isEqualTo("홍길동");
        assertThat(result.getTag()).isEqualTo("00001");
        assertThat(result.getEmail()).isEqualTo("test@gmail.com");
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE.getNumber());
        verify(userJpaRepository).saveAndFlush(argThat(entity ->
                entity.getEmail().equals("test@gmail.com")
                        && entity.getName().equals("홍길동")
                        && entity.getTag().equals("00001")
                        && entity.getStatus() == UserStatus.ACTIVE.getNumber()));
        verify(pendingAuthStore).delete(SESSION_ID);
        verify(pendingAuthStore).saveCompleted(SESSION_ID, 100L);
        verify(userJpaRepository, never()).save(any());
    }

    @Test
    void registerUser_pending세션은_INSERT가_성공한_뒤에_삭제되고_완료마커가_남는다() {
        // D6. save() 면 INSERT 가 트랜잭션 커밋 시점(= 메서드 리턴 후)에 실행되어,
        // 제약 위반이 터질 때 pending 세션이 이미 지워져 사용자 토큰이 죽는다.
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());

        authService.registerUser(SESSION_ID, "홍길동", "00001");

        InOrder inOrder = inOrder(userJpaRepository, pendingAuthStore);
        inOrder.verify(userJpaRepository).saveAndFlush(any());
        inOrder.verify(pendingAuthStore).delete(SESSION_ID);
        inOrder.verify(pendingAuthStore).saveCompleted(SESSION_ID, 100L);
    }

    private PendingOAuthProfile applePendingProfile() {
        return new PendingOAuthProfile("abc@privaterelay.appleid.com", "apple-sub-001",
                OauthType.APPLE, null, "com.danmalgi.mobile", "v1:encrypted");
    }

    @Test
    void registerUser_pending에_refresh_token이_있으면_users_INSERT_후_세션삭제_전에_저장한다() {
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(applePendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());

        authService.registerUser(SESSION_ID, "홍길동", "00001");

        // FK 때문에 users 다음이어야 하고, 실패 시 세션이 남아 있어야 하므로 삭제 전이어야 한다.
        InOrder inOrder = inOrder(userJpaRepository, userOAuthIdentityJpaRepository, userOAuthCredentialJpaRepository, pendingAuthStore);
        inOrder.verify(userJpaRepository).saveAndFlush(any());
        inOrder.verify(userOAuthIdentityJpaRepository).saveAndFlush(any());
        inOrder.verify(userOAuthCredentialJpaRepository).upsert(IDENTITY_ID, "com.danmalgi.mobile", "v1:encrypted");
        inOrder.verify(pendingAuthStore).delete(SESSION_ID);
    }

    @Test
    void registerUser_refresh_token_저장이_실패하면_pending세션을_지우지_않는다() {
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(applePendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());
        when(userOAuthCredentialJpaRepository.upsert(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("fk violation"));

        assertThatThrownBy(() -> authService.registerUser(SESSION_ID, "홍길동", "00001"))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(pendingAuthStore, never()).delete(any());
        verify(pendingAuthStore, never()).saveCompleted(any(), any());
    }

    @Test
    void registerUser_pending의_제공자_계정으로_신원을_만든다() {
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());

        authService.registerUser(SESSION_ID, "홍길동", "00001");

        // 로그인 조회 키는 users 가 아니라 이 신원의 (provider, provider_subject) 다.
        verify(userOAuthIdentityJpaRepository).saveAndFlush(argThat(identity ->
                identity.getUser().getId().equals(100L)
                        && identity.getProvider() == OauthType.GOOGLE
                        && identity.getProviderSubject().equals("google-sub-123")
                        && identity.getEmail().equals("test@gmail.com")
                        && identity.getLastAuthenticatedAt() != null));
    }

    @Test
    void registerUser_신원_INSERT가_실패하면_pending세션을_지우지_않는다() {
        // 같은 제공자 계정이 이미 다른 유저로 가입한 경합. 세션이 남아야 재로그인으로 복구된다.
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("uk_user_oauth_identities_provider_subject"))
                .when(userOAuthIdentityJpaRepository).saveAndFlush(any());

        assertThatThrownBy(() -> authService.registerUser(SESSION_ID, "홍길동", "00001"))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(pendingAuthStore, never()).delete(any());
    }

    @Test
    void registerUser_GOOGLE_pending이면_refresh_token을_저장하지_않는다() {
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());

        authService.registerUser(SESSION_ID, "홍길동", "00001");

        verify(userOAuthCredentialJpaRepository, never()).upsert(any(), any(), any());
    }

    @Test
    void registerUser_닉네임이_중복이면_INSERT도_세션삭제도_하지_않는다() {
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001"))
                .thenReturn(Optional.of(UserEntity.from(new User(2L, "other@test.com", "홍길동", "00001",
                        null, UserStatus.ACTIVE.getNumber()))));

        assertThatThrownBy(() -> authService.registerUser(SESSION_ID, "홍길동", "00001"))
                .isInstanceOf(DuplicatedUserException.class);

        // pending 세션 확인이 중복 확인보다 먼저다 (D6). 순서가 뒤집히면 멱등 재시도가
        // 자기 행에 걸려 DuplicatedUserException 이 된다.
        InOrder inOrder = inOrder(pendingAuthStore, userJpaRepository);
        inOrder.verify(pendingAuthStore).find(SESSION_ID);
        inOrder.verify(userJpaRepository).findByNameAndTag("홍길동", "00001");
        verify(userJpaRepository, never()).saveAndFlush(any());
        verify(userJpaRepository, never()).save(any());
        verify(pendingAuthStore, never()).delete(any());
    }

    @Test
    void registerUser_INSERT가_유니크제약_위반이면_pending세션을_지우지_않는다() {
        // 중복 확인과 INSERT 사이 경합. 세션이 남아야 사용자가 다른 닉네임으로 즉시 재시도할 수 있다.
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());
        when(userJpaRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_users_name_tag"));

        assertThatThrownBy(() -> authService.registerUser(SESSION_ID, "홍길동", "00001"))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(pendingAuthStore, never()).delete(any());
    }

    @Test
    void registerUser_pending세션도_완료마커도_없으면_PendingRegistrationNotFoundException() {
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.empty());
        when(pendingAuthStore.findCompletedUserId(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registerUser(SESSION_ID, "홍길동", "00001"))
                .isInstanceOf(PendingRegistrationNotFoundException.class);

        verify(userJpaRepository, never()).saveAndFlush(any());
        verify(userJpaRepository, never()).save(any());
    }

    @Test
    void registerUser_완료마커가_있으면_INSERT없이_멱등_성공한다() {
        // 응답 유실 후 재시도. pending 세션은 이미 지워졌고 완료 마커만 남아 있다.
        UserEntity registered = registeredUser();
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.empty());
        when(pendingAuthStore.findCompletedUserId(SESSION_ID)).thenReturn(Optional.of(100L));
        when(userJpaRepository.findById(100L)).thenReturn(Optional.of(registered));

        User result = authService.registerUser(SESSION_ID, "홍길동", "00001");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getName()).isEqualTo("홍길동");
        assertThat(result.getTag()).isEqualTo("00001");
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE.getNumber());
        verify(userJpaRepository, never()).saveAndFlush(any());
        verify(userJpaRepository, never()).save(any());
        verify(pendingAuthStore, never()).delete(any());
        verify(pendingAuthStore, never()).saveCompleted(any(), any());
    }

    @Test
    void registerUser_완료마커로_찾은_유저가_없으면_PendingRegistrationNotFoundException() {
        // 완료 마커 TTL(30분)이 끝났거나 데이터가 어긋난 극단적인 경우다.
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.empty());
        when(pendingAuthStore.findCompletedUserId(SESSION_ID)).thenReturn(Optional.of(100L));
        when(userJpaRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registerUser(SESSION_ID, "홍길동", "00001"))
                .isInstanceOf(PendingRegistrationNotFoundException.class);
    }

    @Test
    void registerUser_이미_등록된_계정은_다른_닉네임으로_재호출해도_개명되지_않는다() {
        // 기존 UPDATE 구현은 사실상 rename API 였다. 의도된 조임 (D6).
        UserEntity registered = registeredUser();
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.empty());
        when(pendingAuthStore.findCompletedUserId(SESSION_ID)).thenReturn(Optional.of(100L));
        when(userJpaRepository.findById(100L)).thenReturn(Optional.of(registered));

        User result = authService.registerUser(SESSION_ID, "새이름", "00002");

        assertThat(result.getName()).isEqualTo("홍길동");
        assertThat(result.getTag()).isEqualTo("00001");
        assertThat(registered.getName()).isEqualTo("홍길동");
        assertThat(registered.getTag()).isEqualTo("00001");
        verify(userJpaRepository, never()).save(any());
        verify(userJpaRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerUser_반환값의_profileImageUrl은_조립된_public_URL이다() {
        // @CachePut 은 이 반환값을 user:{id} 에 덮어쓴다.
        // 이 경로가 누락되면 회원가입 직후 TTL 2분간 chat/webrtc 가 raw key 를 본다.
        UserEntity savedUser = UserEntity.registerNew("test@gmail.com", "홍길동", "00001",
                "profiles/100/abc.webp");
        ReflectionTestUtils.setField(savedUser, "id", 100L);
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());
        when(userJpaRepository.saveAndFlush(any())).thenReturn(savedUser);
        when(r2Uploader.toPublicUrl("profiles/100/abc.webp"))
                .thenReturn("https://cdn.example.com/profiles/100/abc.webp");

        User result = authService.registerUser(SESSION_ID, "홍길동", "00001");

        assertThat(result.getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/100/abc.webp");
        verify(r2Uploader).toPublicUrl("profiles/100/abc.webp");
    }
}
