package com.danmalgi.backend.auth.service;

import java.time.Instant;
import java.util.*;

import com.danmalgi.backend.user.domain.exception.DuplicatedUserException;
import com.danmalgi.backend.auth.domain.exception.AlreadyRegisteredException;
import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException;
import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.exception.PendingRegistrationNotFoundException;
import com.danmalgi.backend.auth.domain.exception.UnsupportedOauthTypeException;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthAuthorizationCodeExchangePort;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.auth.repository.entity.UserOAuthIdentityEntity;
import com.danmalgi.backend.auth.repository.persistence.UserOAuthCredentialJpaRepository;
import com.danmalgi.backend.auth.repository.persistence.UserOAuthIdentityJpaRepository;
import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.device.domain.Device;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.device.service.DeviceService;
import com.danmalgi.backend.global.infrastructure.crypto.CredentialCipher;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danmalgi.backend.auth.grpc.dto.AuthorizeResponse;
import com.danmalgi.backend.device.repository.entity.DeviceEntity;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.domain.model.User;

@Slf4j
@Service
public class AuthService {
    private final UserJpaRepository userJpaRepository;
    private final DeviceJpaRepository deviceJpaRepository;
    private final DeviceService deviceService;
    private final Map<OauthType, OAuthPlatformAuthorizationPort> oauthAuthorizationPortByType;
    private final JwtTokenProvider jwtTokenProvider;
    private final R2Uploader r2Uploader;
    private final PendingAuthStore pendingAuthStore;
    private final Map<OauthType, OAuthAuthorizationCodeExchangePort> codeExchangePortByType;
    private final CredentialCipher credentialCipher;
    private final UserOAuthCredentialJpaRepository userOAuthCredentialJpaRepository;
    private final UserOAuthIdentityJpaRepository userOAuthIdentityJpaRepository;

    @Autowired
    public AuthService(
            UserJpaRepository userJpaRepository,
            DeviceJpaRepository deviceJpaRepository,
            DeviceService deviceService,
            JwtTokenProvider jwtTokenProvider,
            List<OAuthPlatformAuthorizationPort> oauthPlatformAuthorizationPorts,
            R2Uploader r2Uploader,
            PendingAuthStore pendingAuthStore,
            List<OAuthAuthorizationCodeExchangePort> codeExchangePorts,
            CredentialCipher credentialCipher,
            UserOAuthCredentialJpaRepository userOAuthCredentialJpaRepository,
            UserOAuthIdentityJpaRepository userOAuthIdentityJpaRepository
    ) {
        this.userOAuthIdentityJpaRepository = userOAuthIdentityJpaRepository;
        this.credentialCipher = credentialCipher;
        this.userOAuthCredentialJpaRepository = userOAuthCredentialJpaRepository;
        this.codeExchangePortByType = new EnumMap<>(OauthType.class);
        for (OAuthAuthorizationCodeExchangePort codeExchangePort : codeExchangePorts) {
            if (codeExchangePortByType.put(codeExchangePort.supportedOauthType(), codeExchangePort) != null) {
                throw new IllegalStateException(
                        "Duplicate authorization code exchange adapter for oauthType: " + codeExchangePort.supportedOauthType());
            }
        }

        this.deviceJpaRepository = deviceJpaRepository;
        this.deviceService = deviceService;
        this.userJpaRepository = userJpaRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.oauthAuthorizationPortByType = new EnumMap<>(OauthType.class);
        this.r2Uploader = r2Uploader;
        this.pendingAuthStore = pendingAuthStore;

        for (OAuthPlatformAuthorizationPort oauthPlatformAuthorizationPort : oauthPlatformAuthorizationPorts) {
            OauthType oauthType = oauthPlatformAuthorizationPort.supportedOauthType();
            OAuthPlatformAuthorizationPort previousPort = oauthAuthorizationPortByType.put(oauthType, oauthPlatformAuthorizationPort);
            if (previousPort != null) {
                throw new IllegalStateException("Duplicate OAuth authorization adapter for oauthType: " + oauthType);
            }
        }
    }

    public AuthorizeResponse authorize(OAuthCredential credential, String deviceId, OauthType oauthType) {
        OAuthPlatformAuthorizationPort oauthAuthorizationPort = oauthAuthorizationPortByType.get(oauthType);
        if (oauthAuthorizationPort == null) {
            throw new UnsupportedOauthTypeException("OAuth type is not supported: " + oauthType);
        }

        PendingOAuthProfile pendingProfile = oauthAuthorizationPort.authorize(credential);
        if (pendingProfile == null) {
            throw new OauthAuthorizeFailException("OAuth authorization failed");
        }

        Optional<UserOAuthIdentityEntity> persistedIdentity = userOAuthIdentityJpaRepository
                .findByProviderAndProviderSubject(pendingProfile.getOauthType(), pendingProfile.getIdentifyId());

        OAuthAuthorizationCodeExchangePort codeExchangePort = codeExchangePortByType.get(oauthType);

        if (persistedIdentity.isPresent()) {
            // 기존 유저는 DB 값을 응답한다. 오늘은 어댑터가 만든 name/tag/PENDING 이
            // 그대로 나갔고(이슈 #25), 그래서 클라이언트의 신규 판별이 동작하지 않았다.
            UserOAuthIdentityEntity identity = persistedIdentity.get();
            User user = identity.getUser().toDomainUser();
            // 제공자 이메일만 갱신한다. users.email(대표 이메일)은 연동이 생기면 고르는 규칙이 따로 필요해 건드리지 않는다.
            userOAuthIdentityJpaRepository.touchAuthenticated(identity.getId(), pendingProfile.getEmail(), Instant.now());
            if (codeExchangePort != null) {
                refreshStoredCredential(codeExchangePort, credential, pendingProfile, identity.getId());
            }
            user.applyPublicProfileImageUrl(r2Uploader);
            return AuthorizeResponse.ofRegisteredUser(
                    user, jwtTokenProvider.generateToken(user.getId(), deviceId));
        }

        // 신규 유저는 토큰 없이 가입시키면 나중에 revoke 할 수 없으므로 교환 실패 시 로그인을 막는다.
        // 세션을 만들기 전에 하는 이유: 실패할 요청 때문에 pending 세션을 만들지 않는다.
        if (codeExchangePort != null) {
            pendingProfile.setEncryptedRefreshToken(
                    exchangeAndEncrypt(codeExchangePort, credential, pendingProfile));
        }

        String sessionId = UUID.randomUUID().toString();
        pendingAuthStore.save(sessionId, pendingProfile);

        return AuthorizeResponse.ofPendingProfile(
                pendingProfile,
                jwtTokenProvider.generatePendingToken(sessionId, deviceId)
        );
    }

    /**
     * 기존 유저는 교환에 실패해도 로그인시킨다. 저장된 토큰이 있으면 그것으로 revoke 할 수 있고,
     * 없더라도(PoC 로 만든 계정 등) 다음 로그인에서 교환에 성공하면 채워진다.
     *
     * <p>이 정책은 idToken 만으로 로그인이 된다는 뜻이라, 어댑터의 nonce 1회 소비가 전제다.
     * nonce 없는 구 앱 토큰은 아직 재사용을 막지 못한다 (oauth.apple.nonce-required).
     *
     * <p>예외: code 가 다른 계정의 것(SUBJECT_MISMATCH)이면 로그인을 막는다. 정상 앱에서는
     * 생길 수 없고, 다른 계정의 토큰을 섞으려는 요청으로 본다.
     * DB 오류는 삼키지 않는다. 교환은 성공했는데 저장이 안 되는 것은 알아야 하는 장애다.
     */
    private void refreshStoredCredential(
            OAuthAuthorizationCodeExchangePort codeExchangePort,
            OAuthCredential credential,
            PendingOAuthProfile pendingProfile,
            Long identityId
    ) {
        String encryptedRefreshToken;
        try {
            encryptedRefreshToken = exchangeAndEncrypt(codeExchangePort, credential, pendingProfile);
        } catch (AuthorizationCodeExchangeException e) {
            if (e.getReason() == AuthorizationCodeExchangeException.Reason.SUBJECT_MISMATCH) {
                throw e;
            }
            log.warn("Refresh token not updated, login allowed: identityId={}, reason={}", identityId, e.getReason());
            return;
        }
        userOAuthCredentialJpaRepository.upsert(identityId, pendingProfile.getOauthClientId(), encryptedRefreshToken);
    }

    private String exchangeAndEncrypt(
            OAuthAuthorizationCodeExchangePort codeExchangePort,
            OAuthCredential credential,
            PendingOAuthProfile pendingProfile
    ) {
        if (credential.authorizationCode() == null) {
            throw new AuthorizationCodeExchangeException(
                    AuthorizationCodeExchangeException.Reason.REJECTED, "authorization_code is missing");
        }
        String refreshToken = codeExchangePort.exchange(
                credential.authorizationCode(), pendingProfile.getOauthClientId(), pendingProfile.getIdentifyId());
        // 평문은 이 메서드 밖으로 내보내지 않는다. Redis(pending)와 DB 에 같은 암호문이 들어간다.
        return credentialCipher.encrypt(refreshToken);
    }

    /**
     * {@code @CachePut(key = "#p0")} 를 쓰지 않는 이유: 첫 인자가 이제 userId 가 아니라
     * pendingSessionId(String)라 캐시 키가 될 수 없다. {@code #result.id} 로 돌려 쓴다.
     */
    @CachePut(value = "user", key = "#result.id")
    @Transactional
    public User registerUser(String pendingSessionId, String name, String tag) {
        if (pendingSessionId == null) {
            // 등록 완료 토큰(userId 클레임)으로 Register 를 부른 경우다. 정상 클라이언트는
            // 이 경로를 타지 않는다 — 인터셉터가 pending 토큰에만 PENDING_SESSION_ID 를 심는다.
            throw new AlreadyRegisteredException("token does not identify a pending registration");
        }

        String normalizedName = name.trim();
        String normalizedTag = tag.trim();

        Optional<PendingOAuthProfile> pendingProfile = pendingAuthStore.find(pendingSessionId);
        if (pendingProfile.isEmpty()) {
            // 이미 완료된 Register 의 재시도이면 완료 마커로 같은 유저를 찾아 멱등 성공시킨다.
            // 응답 유실 후 재시도가 현실적이다. 단 다른 닉네임으로 재호출해도 개명되지는 않는다.
            Long completedUserId = pendingAuthStore.findCompletedUserId(pendingSessionId)
                    .orElseThrow(() -> new PendingRegistrationNotFoundException(
                            "pending registration not found: sessionId=" + pendingSessionId));
            return userJpaRepository.findById(completedUserId)
                    .map(this::toPublicDomainUser)
                    .orElseThrow(() -> new PendingRegistrationNotFoundException(
                            "pending registration not found: sessionId=" + pendingSessionId));
        }

        if (userJpaRepository.findByNameAndTag(normalizedName, normalizedTag).isPresent()) {
            throw new DuplicatedUserException("name and tag already in use");
        }

        PendingOAuthProfile profile = pendingProfile.get();
        UserEntity userEntity = UserEntity.registerNew(
                profile.getEmail(),
                normalizedName,
                normalizedTag,
                profile.getProfileImageUrl()
        );

        // saveAndFlush: INSERT 를 이 메서드 안에서 터뜨려야 uk_users_name_tag 경합이
        // pending 세션 삭제보다 먼저 드러난다. 커밋 시점 flush 면 세션을 이미 지운 뒤
        // 실패해 사용자의 토큰이 죽는다.
        UserEntity savedUser = userJpaRepository.saveAndFlush(userEntity);

        // 신원 INSERT 도 같은 이유로 flush 한다. uk_user_oauth_identities_provider_subject 위반
        // (같은 계정이 이미 다른 유저로 가입한 경합)이 pending 삭제 전에 드러나야 한다.
        UserOAuthIdentityEntity identity = userOAuthIdentityJpaRepository.saveAndFlush(
                UserOAuthIdentityEntity.link(savedUser, profile.getOauthType(), profile.getIdentifyId(),
                        profile.getEmail(), Instant.now()));

        // 신원 행이 생긴 뒤라 FK 를 만족한다. 같은 트랜잭션이라 여기서 실패하면 users/신원 INSERT 도
        // 롤백되고, pending 세션은 아직 지우지 않았으므로 사용자는 Register 를 다시 할 수 있다.
        if (profile.getEncryptedRefreshToken() != null) {
            userOAuthCredentialJpaRepository.upsert(
                    identity.getId(), profile.getOauthClientId(), profile.getEncryptedRefreshToken());
        }

        // 삭제와 완료 마커 기록 사이에 서버가 죽으면 재시도 시 pending 세션도 완료 마커도 없는
        // 좁은 창이 생긴다 — 그 경우는 PendingRegistrationNotFoundException 으로 드러나고
        // 복구 경로는 Authorization 재호출이다.
        pendingAuthStore.delete(pendingSessionId);
        pendingAuthStore.saveCompleted(pendingSessionId, savedUser.getId());

        // @CachePut 이 이 반환값을 user:{id} 에 덮어쓴다. 여기서 조립하지 않으면
        // 회원가입 직후 TTL 2분간 chat/webrtc 가 raw object key 를 본다.
        return toPublicDomainUser(savedUser);
    }

    /** Register 가 호출에 쓴 pending 토큰을 실 userId 토큰으로 바꿔주기 위한 재발급. */
    public String issueAccessToken(Long userId, String deviceId) {
        return jwtTokenProvider.generateToken(userId, deviceId);
    }

    private User toPublicDomainUser(UserEntity userEntity) {
        User user = userEntity.toDomainUser();
        user.applyPublicProfileImageUrl(r2Uploader);
        return user;
    }

    @Transactional
    public void upsertFcmToken(Long userId, String deviceId, String fcmToken) {
        String normalizedDeviceId = deviceId.trim();
        String normalizedFcmToken = fcmToken.trim();

        UserEntity userEntity = userJpaRepository.findById(userId).orElseThrow();
        DeviceEntity deviceEntity = deviceJpaRepository.findFirstByDeviceId(normalizedDeviceId)
                .map(existingDevice -> {
                    existingDevice.updateUserIdAndFcmToken(normalizedFcmToken);
                    return existingDevice;
                })
                .orElseGet(() -> DeviceEntity.of(userEntity, normalizedDeviceId, normalizedFcmToken));

        deviceJpaRepository.save(deviceEntity);
        deviceService.putDevice(
                normalizedDeviceId,
                new Device(userId, normalizedDeviceId, normalizedFcmToken)
        );
    }
}
