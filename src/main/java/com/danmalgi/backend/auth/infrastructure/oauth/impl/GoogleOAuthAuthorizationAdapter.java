package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.exception.OauthProviderUnavailableException;
import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.auth.infrastructure.oauth.impl.IdTokenVerificationException.Kind;
import com.danmalgi.backend.user.domain.model.OauthType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class GoogleOAuthAuthorizationAdapter implements OAuthPlatformAuthorizationPort {
    // 클라이언트에는 거절 이유를 노출하지 않는다. 이유는 서버 로그에만 남긴다.
    private static final String CLIENT_DESCRIPTION = "Invalid Google idToken";

    private final GoogleIdTokenVerifier idTokenVerifier;
    private final Set<String> allowedAudiences;

    @Autowired
    public GoogleOAuthAuthorizationAdapter(
            GoogleIdTokenVerifier idTokenVerifier,
            @Value("${oauth.google.audiences}") List<String> audiences
    ) {
        this.idTokenVerifier = idTokenVerifier;
        this.allowedAudiences = normalizeAudiences(audiences);
    }

    private static Set<String> normalizeAudiences(List<String> audiences) {
        Set<String> allowed = Set.copyOf(audiences.stream()
                .map(String::trim)
                .filter(audience -> !audience.isEmpty())
                .toList());
        if (allowed.isEmpty()) {
            // 조용히 검증이 꺼진 채로 뜨는 것이 설정 누락으로 못 뜨는 것보다 나쁘다.
            throw new IllegalStateException("oauth.google.audiences must not be empty");
        }
        return allowed;
    }

    @Override
    public OauthType supportedOauthType() {
        return OauthType.GOOGLE;
    }

    @Override
    public PendingOAuthProfile authorize(OAuthCredential credential) {
        // rawNonce 는 Apple 전용이라 읽지 않는다. Google 은 nonce 를 소비하지 않으므로
        // 유출된 토큰을 exp 전까지 재사용할 수 있다 — 아직 막지 못하는 빈틈이다.
        Jwt jwt = verify(credential.idToken());

        // 계정 식별 키는 (GOOGLE, sub) 다. email 은 바뀔 수 있어 식별에 쓰지 않는다.
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            log.warn("Google idToken rejected: sub claim is missing");
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }

        // email 은 UserEntity.email 이 @NotNull 이라 필수다.
        // email_verified 는 읽지 않는다. email 을 식별·연동에 쓰게 되면 그때 검사해야 한다.
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            log.warn("Google idToken rejected: email claim is missing, sub={}", sub);
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }

        // name 클레임은 검사하지 않는다. profile 스코프가 없으면 실리지 않는 값이고,
        // normalizeName 제거로 소비자가 사라졌다 (이슈 #37 요구사항 3).
        return PendingOAuthProfile.builder()
                .email(email)
                .identifyId(sub)
                .oauthType(supportedOauthType())
                // oauthClientId 는 채우지 않는다. Apple 은 code 교환의 client_id 로 쓰지만
                // Google 은 교환 포트가 없어 읽는 쪽이 없다. 채우면 쓰이는 값처럼 보인다.
                .build();
    }

    private Jwt verify(String idToken) {
        try {
            return idTokenVerifier.verify(idToken, allowedAudiences);
        } catch (IdTokenVerificationException e) {
            if (e.kind() == Kind.UNAVAILABLE) {
                log.error("Google idToken verification unavailable: {}", e.getMessage());
                throw new OauthProviderUnavailableException("Google JWKS unavailable", e);
            }
            log.warn("Google idToken rejected: {}", e.getMessage());
            throw new OauthAuthorizeFailException(CLIENT_DESCRIPTION);
        }
    }
}
