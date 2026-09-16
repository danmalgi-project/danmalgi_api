package com.danmalgi.backend.auth.service;

import java.util.*;

import com.danmalgi.backend.user.domain.exception.DuplicatedUserException;
import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.exception.PendingRegistrationNotFoundException;
import com.danmalgi.backend.auth.domain.exception.UnsupportedOauthTypeException;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.device.domain.Device;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.device.service.DeviceService;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danmalgi.backend.auth.grpc.dto.AuthorizeResponse;
import com.danmalgi.backend.device.repository.entity.DeviceEntity;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.domain.model.User;

@Service
public class AuthService {
    private final UserJpaRepository userJpaRepository;
    private final DeviceJpaRepository deviceJpaRepository;
    private final DeviceService deviceService;
    private final Map<OauthType, OAuthPlatformAuthorizationPort> oauthAuthorizationPortByType;
    private final JwtTokenProvider jwtTokenProvider;
    private final R2Uploader r2Uploader;
    private final PendingAuthStore pendingAuthStore;

    @Autowired
    public AuthService(
            UserJpaRepository userJpaRepository,
            DeviceJpaRepository deviceJpaRepository,
            DeviceService deviceService,
            JwtTokenProvider jwtTokenProvider,
            List<OAuthPlatformAuthorizationPort> oauthPlatformAuthorizationPorts,
            R2Uploader r2Uploader,
            PendingAuthStore pendingAuthStore
    ) {
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

    public AuthorizeResponse authorize(String idToken, String deviceId, OauthType oauthType) {
        OAuthPlatformAuthorizationPort oauthAuthorizationPort = oauthAuthorizationPortByType.get(oauthType);
        if (oauthAuthorizationPort == null) {
            throw new UnsupportedOauthTypeException("OAuth type is not supported: " + oauthType);
        }

        PendingOAuthProfile pendingProfile = oauthAuthorizationPort.authorize(idToken);
        if (pendingProfile == null) {
            throw new OauthAuthorizeFailException("OAuth authorization failed");
        }

        Optional<UserEntity> persistedUser = userJpaRepository
                .findByOauthTypeAndIdentifyId(pendingProfile.getOauthType(), pendingProfile.getIdentifyId());

        if (persistedUser.isPresent()) {
            // 기존 유저는 DB 값을 응답한다. 오늘은 어댑터가 만든 name/tag/PENDING 이
            // 그대로 나갔고(이슈 #25), 그래서 클라이언트의 신규 판별이 동작하지 않았다.
            User user = persistedUser.get().toDomainUser();
            user.applyPublicProfileImageUrl(r2Uploader);
            return AuthorizeResponse.ofRegisteredUser(
                    user, jwtTokenProvider.generateToken(user.getId(), deviceId));
        }

        pendingProfile.setUserId(resolvePendingUserId(pendingProfile));
        pendingAuthStore.save(pendingProfile);

        return AuthorizeResponse.ofPendingProfile(
                pendingProfile,
                jwtTokenProvider.generateToken(pendingProfile.getUserId(), deviceId)
        );
    }

    /**
     * pending 세션의 userId 를 확정한다.
     *
     * <p>TTL 내 재호출이면 <b>기존 id 를 재사용</b>해 선행 호출로 발급한 토큰이 계속
     * 유효하도록 한다. 새 id 를 뽑으면 그 토큰의 userId 클레임이 가리키는 세션이 사라진다.
     */
    private Long resolvePendingUserId(PendingOAuthProfile pendingProfile) {
        Optional<Long> reusableUserId = pendingAuthStore
                .findUserId(pendingProfile.getOauthType(), pendingProfile.getIdentifyId());
        if (reusableUserId.isPresent()) {
            return reusableUserId.get();
        }

        Long nextUserId = userJpaRepository.nextUserId();
        if (nextUserId == null) {
            throw new IllegalStateException(
                    "users.id sequence not found: check pg_get_serial_sequence('users', 'id')");
        }

        if (pendingAuthStore.claimUserId(
                pendingProfile.getOauthType(), pendingProfile.getIdentifyId(), nextUserId)) {
            return nextUserId;
        }

        // 동시 요청이 먼저 선점했다 — 승자의 id 를 따른다.
        // 버려진 nextUserId 는 시퀀스 gap 으로 남는다 (PostgreSQL 시퀀스는 롤백되지 않는다).
        return pendingAuthStore
                .findUserId(pendingProfile.getOauthType(), pendingProfile.getIdentifyId())
                .orElse(nextUserId);
    }

    @CachePut(value = "user", key = "#p0")
    @Transactional
    public User registerUser(Long userId, String name, String tag) {
        String normalizedName = name.trim();
        String normalizedTag = tag.trim();

        Optional<PendingOAuthProfile> pendingProfile = pendingAuthStore.find(userId);
        if (pendingProfile.isEmpty()) {
            // 이미 완료된 Register 의 재시도이면 기존과 같이 멱등 성공시킨다.
            // 응답 유실 후 재시도가 현실적이고, 기존 코드도 findByNameAndTag 자기-예외로
            // 같은 결과를 냈다. 단 다른 닉네임으로 재호출해도 개명되지는 않는다.
            return userJpaRepository.findById(userId)
                    .map(this::toPublicDomainUser)
                    .orElseThrow(() -> new PendingRegistrationNotFoundException(
                            "pending registration not found: id=" + userId));
        }

        // 이 지점에서 userId 의 행은 존재하지 않음이 보장되므로, 히트는 모두 남의 행이다.
        if (userJpaRepository.findByNameAndTag(normalizedName, normalizedTag).isPresent()) {
            throw new DuplicatedUserException("name and tag already in use");
        }

        PendingOAuthProfile profile = pendingProfile.get();
        UserEntity userEntity = UserEntity.registerNew(
                userId,
                profile.getEmail(),
                normalizedName,
                normalizedTag,
                profile.getIdentifyId(),
                profile.getOauthType(),
                profile.getProfileImageUrl()
        );

        // saveAndFlush: INSERT 를 이 메서드 안에서 터뜨려야 uk_users_name_tag 경합이
        // pending 세션 삭제보다 먼저 드러난다. 커밋 시점 flush 면 세션을 이미 지운 뒤
        // 실패해 사용자의 토큰이 죽는다.
        UserEntity savedUser = userJpaRepository.saveAndFlush(userEntity);

        pendingAuthStore.delete(profile);

        // @CachePut 이 이 반환값을 user:{id} 에 덮어쓴다. 여기서 조립하지 않으면
        // 회원가입 직후 TTL 2분간 chat/webrtc 가 raw object key 를 본다.
        return toPublicDomainUser(savedUser);
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
