package com.danmalgi.backend.auth.service;

import com.danmalgi.backend.auth.domain.exception.PendingRegistrationNotFoundException;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.device.service.DeviceService;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterUserTest {

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
                pendingAuthStore
        );
    }

    /** Authorization 단계에서 userId 100 을 확보해 만든 pending 세션. 해당 users 행은 아직 없다. */
    private PendingOAuthProfile pendingProfile() {
        return new PendingOAuthProfile(
                100L, "test@gmail.com", "google-sub-123", OauthType.GOOGLE.getNumber(), null);
    }

    private UserEntity registeredUser() {
        return UserEntity.from(new User(100L, "test@gmail.com", "홍길동", "00001", null,
                "google-sub-123", OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber()));
    }

    @Test
    void registerUser_pending세션이_있으면_ACTIVE로_최초_INSERT하고_세션을_지운다() {
        when(pendingAuthStore.find(100L)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());
        when(userJpaRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.registerUser(100L, "  홍길동  ", "  00001  ");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getName()).isEqualTo("홍길동");
        assertThat(result.getTag()).isEqualTo("00001");
        assertThat(result.getEmail()).isEqualTo("test@gmail.com");
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE.getNumber());
        // isNew 가 false 면 Spring Data 가 merge 를 타고, merge 는 같은 id 의 행이 이미 있을 때
        // INSERT 대신 조용히 UPDATE 한다 (다른 유저 행 덮어쓰기).
        verify(userJpaRepository).saveAndFlush(argThat(entity ->
                entity.isNew()
                        && entity.getId().equals(100L)
                        && entity.getEmail().equals("test@gmail.com")
                        && entity.getName().equals("홍길동")
                        && entity.getTag().equals("00001")
                        && entity.getIdentifyId().equals("google-sub-123")
                        && entity.getOauthType() == OauthType.GOOGLE.getNumber()
                        && entity.getStatus() == UserStatus.ACTIVE.getNumber()));
        verify(pendingAuthStore).delete(argThat(profile -> profile.getUserId().equals(100L)));
        verify(userJpaRepository, never()).save(any());
    }

    @Test
    void registerUser_pending세션은_INSERT가_성공한_뒤에_삭제된다() {
        // D6. save() 면 INSERT 가 트랜잭션 커밋 시점(= 메서드 리턴 후)에 실행되어,
        // 제약 위반이 터질 때 pending 세션이 이미 지워져 사용자 토큰이 죽는다.
        when(pendingAuthStore.find(100L)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());
        when(userJpaRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        authService.registerUser(100L, "홍길동", "00001");

        InOrder inOrder = inOrder(userJpaRepository, pendingAuthStore);
        inOrder.verify(userJpaRepository).saveAndFlush(any());
        inOrder.verify(pendingAuthStore).delete(any());
    }

    @Test
    void registerUser_닉네임이_중복이면_INSERT도_세션삭제도_하지_않는다() {
        when(pendingAuthStore.find(100L)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001"))
                .thenReturn(Optional.of(UserEntity.from(new User(2L, "other@test.com", "홍길동", "00001",
                        null, "other-sub", OauthType.KAKAO.getNumber(), UserStatus.ACTIVE.getNumber()))));

        assertThatThrownBy(() -> authService.registerUser(100L, "홍길동", "00001"))
                .isInstanceOf(DuplicatedUserException.class);

        // pending 세션 확인이 중복 확인보다 먼저다 (D6). 순서가 뒤집히면 멱등 재시도가
        // 자기 행에 걸려 DuplicatedUserException 이 된다.
        InOrder inOrder = inOrder(pendingAuthStore, userJpaRepository);
        inOrder.verify(pendingAuthStore).find(100L);
        inOrder.verify(userJpaRepository).findByNameAndTag("홍길동", "00001");
        verify(userJpaRepository, never()).saveAndFlush(any());
        verify(userJpaRepository, never()).save(any());
        verify(pendingAuthStore, never()).delete(any());
    }

    @Test
    void registerUser_INSERT가_유니크제약_위반이면_pending세션을_지우지_않는다() {
        // 중복 확인과 INSERT 사이 경합. 세션이 남아야 사용자가 다른 닉네임으로 즉시 재시도할 수 있다.
        when(pendingAuthStore.find(100L)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());
        when(userJpaRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_users_name_tag"));

        assertThatThrownBy(() -> authService.registerUser(100L, "홍길동", "00001"))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(pendingAuthStore, never()).delete(any());
    }

    @Test
    void registerUser_pending세션이_없고_DB행도_없으면_PendingRegistrationNotFoundException() {
        when(pendingAuthStore.find(999L)).thenReturn(Optional.empty());
        when(userJpaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registerUser(999L, "홍길동", "00001"))
                .isInstanceOf(PendingRegistrationNotFoundException.class);

        verify(userJpaRepository, never()).saveAndFlush(any());
        verify(userJpaRepository, never()).save(any());
    }

    @Test
    void registerUser_pending세션이_없지만_DB행이_있으면_INSERT없이_멱등_성공한다() {
        // 응답 유실 후 재시도. 기존 코드도 findByNameAndTag 자기-예외로 멱등 성공했다.
        UserEntity registered = registeredUser();
        when(pendingAuthStore.find(100L)).thenReturn(Optional.empty());
        when(userJpaRepository.findById(100L)).thenReturn(Optional.of(registered));

        User result = authService.registerUser(100L, "홍길동", "00001");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getName()).isEqualTo("홍길동");
        assertThat(result.getTag()).isEqualTo("00001");
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE.getNumber());
        verify(userJpaRepository, never()).saveAndFlush(any());
        verify(userJpaRepository, never()).save(any());
        verify(pendingAuthStore, never()).delete(any());
    }

    @Test
    void registerUser_이미_등록된_계정은_다른_닉네임으로_재호출해도_개명되지_않는다() {
        // 기존 UPDATE 구현은 사실상 rename API 였다. 의도된 조임 (D6).
        UserEntity registered = registeredUser();
        when(pendingAuthStore.find(100L)).thenReturn(Optional.empty());
        when(userJpaRepository.findById(100L)).thenReturn(Optional.of(registered));

        User result = authService.registerUser(100L, "새이름", "00002");

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
        UserEntity savedUser = UserEntity.registerNew(100L, "test@gmail.com", "홍길동", "00001",
                "google-sub-123", OauthType.GOOGLE.getNumber(), "profiles/100/abc.webp");
        when(pendingAuthStore.find(100L)).thenReturn(Optional.of(pendingProfile()));
        when(userJpaRepository.findByNameAndTag("홍길동", "00001")).thenReturn(Optional.empty());
        when(userJpaRepository.saveAndFlush(any())).thenReturn(savedUser);
        when(r2Uploader.toPublicUrl("profiles/100/abc.webp"))
                .thenReturn("https://cdn.example.com/profiles/100/abc.webp");

        User result = authService.registerUser(100L, "홍길동", "00001");

        assertThat(result.getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/100/abc.webp");
        verify(r2Uploader).toPublicUrl("profiles/100/abc.webp");
    }
}
