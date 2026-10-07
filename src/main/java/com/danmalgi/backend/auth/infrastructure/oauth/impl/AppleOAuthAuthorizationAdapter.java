package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.user.domain.model.OauthType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
public class AppleOAuthAuthorizationAdapter implements OAuthPlatformAuthorizationPort {
    static final String ISSUER = "https://appleid.apple.com";
    static final String JWK_SET_URI = "https://appleid.apple.com/auth/keys";

    // 클라이언트에는 거절 이유를 노출하지 않는다. 이유는 서버 로그에만 남긴다.
    private static final String CLIENT_DESCRIPTION = "Invalid Apple idToken";

    private final JwtDecoder jwtDecoder;

    @Autowired
    public AppleOAuthAuthorizationAdapter(@Value("${oauth.apple.audiences}") List<String> audiences) {
        this(createJwtDecoder(createRestOperations(), audiences));
    }

    AppleOAuthAuthorizationAdapter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * Apple JWKS 로 RS256 서명을 검증하고 iss/aud/exp 를 확인하는 decoder.
     *
     * <p>JWKS 는 Nimbus {@code JWKSourceBuilder} 기본 캐시(5분)에 보관된다. 헤더의 {@code kid}
     * 가 캐시에 없으면 JWKS 를 다시 받아온다 (Apple 키 교체 대응).
     * exp 는 {@code JwtTimestampValidator} 기본 clock skew 60초를 허용한다.
     */
    static JwtDecoder createJwtDecoder(RestOperations restOperations, List<String> audiences) {
        List<String> allowedAudiences = audiences.stream()
                .map(String::trim)
                .filter(audience -> !audience.isEmpty())
                .toList();
        if (allowedAudiences.isEmpty()) {
            throw new IllegalStateException("oauth.apple.audiences must not be empty");
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(restOperations)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(ISSUER),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.stream().anyMatch(allowedAudiences::contains))
        ));
        return decoder;
    }

    private static RestOperations createRestOperations() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(requestFactory);
    }

    @Override
    public OauthType supportedOauthType() {
        return OauthType.APPLE;
    }

    @Override
    public PendingOAuthProfile authorize(OAuthCredential credential) {
        Jwt jwt = decode(credential.idToken());

        // 계정 식별 키는 (APPLE, sub) 다. email 은 릴레이 주소이거나 바뀔 수 있어 식별에 쓰지 않는다.
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            log.warn("Apple idToken rejected: sub claim is missing");
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }

        verifyNonce(jwt, credential.rawNonce(), sub);

        // email 은 UserEntity.email 이 @NotNull 이라 필수다.
        // email_verified / is_private_email 은 문자열 "true" 와 boolean 이 섞여 오지만 읽지 않는다.
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            log.warn("Apple idToken rejected: email claim is missing, sub={}", sub);
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }

        return PendingOAuthProfile.builder()
                .email(email)
                .identifyId(sub)
                .oauthType(supportedOauthType().getNumber())
                // Apple aud 는 단일 값이고 decoder 가 이미 허용 목록과 대조했다.
                // code 교환의 client_id 로 써야 하므로 iOS/Android 어느 쪽인지 그대로 넘긴다.
                .oauthClientId(jwt.getAudience().getFirst())
                .build();
    }

    /**
     * 앱은 Apple 에 {@code sha256(raw_nonce)} 의 소문자 hex 를 넘기고 서버에는 원문을 보낸다.
     * 원문을 아는 것은 토큰을 요청한 앱뿐이므로, 탈취한 idToken 을 다른 요청에 재사용하는 것을 막는다.
     *
     * <p>미완성: 구 앱은 raw_nonce 를 보내지 않으므로 지금은 있을 때만 검증한다.
     * 새 앱이 배포되고 구 앱이 빠지면 필수로 바꾼다 (이슈 #3 의 3-2).
     */
    private void verifyNonce(Jwt jwt, String rawNonce, String sub) {
        if (rawNonce == null) {
            log.info("Apple idToken accepted without raw_nonce, sub={}", sub);
            return;
        }

        String nonceClaim = jwt.getClaimAsString("nonce");
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

    private Jwt decode(String idToken) {
        try {
            return jwtDecoder.decode(idToken);
        } catch (BadJwtException e) {
            // 서명/kid/iss/aud/exp 불일치. 메시지에 토큰 원문은 포함되지 않는다.
            log.warn("Apple idToken rejected: {}", e.getMessage());
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        } catch (JwtException e) {
            // JWKS 조회 실패 등 토큰 자체와 무관한 오류.
            log.error("Apple idToken verification failed: {}", e.getMessage());
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }
    }
}
