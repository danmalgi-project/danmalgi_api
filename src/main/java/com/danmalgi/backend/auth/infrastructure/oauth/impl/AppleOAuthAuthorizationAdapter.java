package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.exception.OauthProviderUnavailableException;
import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.auth.infrastructure.oauth.impl.AppleIdTokenVerificationException.Kind;
import com.danmalgi.backend.auth.infrastructure.replay.OAuthNonceReplayStore;
import com.danmalgi.backend.user.domain.model.OauthType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class AppleOAuthAuthorizationAdapter implements OAuthPlatformAuthorizationPort {
    // 클라이언트에는 거절 이유를 노출하지 않는다. 이유는 서버 로그에만 남긴다.
    private static final String CLIENT_DESCRIPTION = "Invalid Apple idToken";

    private final AppleIdTokenVerifier idTokenVerifier;
    private final OAuthNonceReplayStore nonceReplayStore;
    private final Set<String> allowedAudiences;
    private final boolean nonceRequired;
    private final Clock clock;

    @Autowired
    public AppleOAuthAuthorizationAdapter(
            AppleIdTokenVerifier idTokenVerifier,
            OAuthNonceReplayStore nonceReplayStore,
            @Value("${oauth.apple.audiences}") List<String> audiences,
            @Value("${oauth.apple.nonce-required:false}") boolean nonceRequired
    ) {
        this(idTokenVerifier, nonceReplayStore, audiences, nonceRequired, Clock.systemUTC());
    }

    AppleOAuthAuthorizationAdapter(
            AppleIdTokenVerifier idTokenVerifier,
            OAuthNonceReplayStore nonceReplayStore,
            List<String> audiences,
            boolean nonceRequired,
            Clock clock
    ) {
        this.idTokenVerifier = idTokenVerifier;
        this.nonceReplayStore = nonceReplayStore;
        this.allowedAudiences = normalizeAudiences(audiences);
        this.nonceRequired = nonceRequired;
        this.clock = clock;
    }

    private static Set<String> normalizeAudiences(List<String> audiences) {
        Set<String> allowed = Set.copyOf(audiences.stream()
                .map(String::trim)
                .filter(audience -> !audience.isEmpty())
                .toList());
        if (allowed.isEmpty()) {
            throw new IllegalStateException("oauth.apple.audiences must not be empty");
        }
        return allowed;
    }

    @Override
    public OauthType supportedOauthType() {
        return OauthType.APPLE;
    }

    @Override
    public PendingOAuthProfile authorize(OAuthCredential credential) {
        Jwt jwt = verify(credential.idToken());

        // 계정 식별 키는 (APPLE, sub) 다. email 은 릴레이 주소이거나 바뀔 수 있어 식별에 쓰지 않는다.
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            log.warn("Apple idToken rejected: sub claim is missing");
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }

        // email 은 UserEntity.email 이 @NotNull 이라 필수다. 기존 유저도 같은 규칙이다.
        // email_verified / is_private_email 은 문자열 "true" 와 boolean 이 섞여 오지만 읽지 않는다.
        // email 을 식별·연동에 쓰게 되면 그때 email_verified 를 검사해야 한다.
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            log.warn("Apple idToken rejected: email claim is missing, sub={}", sub);
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }

        // nonce 소비는 마지막에 한다. 다른 이유로 거절될 토큰 때문에 nonce 를 태우지 않는다.
        verifyNonce(jwt, credential.rawNonce(), sub);

        return PendingOAuthProfile.builder()
                .email(email)
                .identifyId(sub)
                .oauthType(supportedOauthType())
                // verifier 가 aud 가 정확히 1개이고 허용 목록 안임을 확인했다.
                // code 교환의 client_id 로 써야 하므로 iOS/Android 어느 쪽인지 그대로 넘긴다.
                .oauthClientId(jwt.getAudience().getFirst())
                .build();
    }

    private Jwt verify(String idToken) {
        try {
            return idTokenVerifier.verify(idToken, allowedAudiences);
        } catch (AppleIdTokenVerificationException e) {
            if (e.kind() == Kind.UNAVAILABLE) {
                log.error("Apple idToken verification unavailable: {}", e.getMessage());
                throw new OauthProviderUnavailableException("Apple JWKS unavailable", e);
            }
            log.warn("Apple idToken rejected: {}", e.getMessage());
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }
    }

    /**
     * 앱은 Apple 에 {@code sha256(raw_nonce)} 의 소문자 hex 를 넘기고 서버에는 원문을 보낸다.
     * 원문을 아는 것은 토큰을 요청한 앱뿐이고, 검증을 통과한 nonce 는 1회만 쓸 수 있다.
     *
     * <p>{@code nonce_supported} 클레임은 보지 않는다. raw_nonce 를 보냈는데 클레임이 없으면
     * 무조건 거절하므로 Apple 권고보다 엄격한 쪽이다.
     *
     * <p>미완성: 구 앱은 raw_nonce 를 보내지 않으므로 {@code oauth.apple.nonce-required=false}
     * 동안은 nonce 클레임도 raw_nonce 도 없는 토큰을 받는다. 이 토큰은 재사용을 막지 못한다.
     * 구 앱이 빠지면 플래그를 켠다 (이슈 #3 의 3-2).
     */
    private void verifyNonce(Jwt jwt, String rawNonce, String sub) {
        String nonceClaim = jwt.getClaimAsString("nonce");

        if (rawNonce == null) {
            // nonce 클레임이 있는 토큰은 새 앱이 받은 것이다. raw_nonce 만 빼고 보내면 검증을
            // 건너뛰던 다운그레이드를 막는다. 구 앱 토큰에는 클레임이 없으므로 호환은 그대로다.
            if (nonceRequired || nonceClaim != null) {
                log.warn("Apple idToken rejected: raw_nonce is missing, nonceClaimPresent={}, sub={}",
                        nonceClaim != null, sub);
                throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
            }
            log.info("Apple idToken accepted without nonce, sub={}", sub);
            return;
        }

        if (nonceClaim == null) {
            log.warn("Apple idToken rejected: nonce claim is missing, sub={}", sub);
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }

        // 대문자 hex 는 허용하지 않는다. 클라이언트 규약을 소문자 하나로 고정한다.
        byte[] expected = sha256Hex(rawNonce).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, nonceClaim.getBytes(StandardCharsets.UTF_8))) {
            // 원문과 클레임 값은 로그에 남기지 않는다.
            log.warn("Apple idToken rejected: nonce mismatch, sub={}", sub);
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }

        // 여기까지 오면 nonceClaim 은 sha256 hex 64자라 키 길이와 원문 노출을 걱정하지 않아도 된다.
        if (!nonceReplayStore.markUsed(supportedOauthType(), nonceClaim, replayTtl(jwt))) {
            log.warn("Apple idToken rejected: nonce already used, sub={}", sub);
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }
    }

    // exp + skew 까지는 verifier 가 받아 주므로 그때까지 기억한다. verifier 가 exp 를 필수로 검사한다.
    private Duration replayTtl(Jwt jwt) {
        Instant forgetAt = jwt.getExpiresAt().plus(AppleIdTokenVerifier.CLOCK_SKEW);
        Duration ttl = Duration.between(clock.instant(), forgetAt);
        return ttl.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : ttl;
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // 모든 JVM 이 SHA-256 을 제공해야 하므로 여기 오면 런타임 자체가 잘못된 것이다.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
