package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;

/**
 * Apple 이 발급한 id_token 을 검증한다. 로그인 idToken 과 code 교환 응답의 id_token 이 같은
 * 규칙·같은 JWKS 캐시를 쓰도록 한 곳에 둔다.
 *
 * <p>검증 규칙 자체는 {@link OidcIdTokenVerifier} 에 있고 여기에는 Apple 고유 값만 둔다.
 * Apple 은 iss 가 하나이고 JWKS 에 RS256 키만 있다.
 */
@Component
public class AppleIdTokenVerifier {
    static final String ISSUER = "https://appleid.apple.com";
    static final String JWK_SET_URI = "https://appleid.apple.com/auth/keys";
    // 어댑터가 nonce 재사용 방지 TTL 을 exp + skew 로 계산할 때 참조한다.
    static final Duration CLOCK_SKEW = OidcIdTokenVerifier.CLOCK_SKEW;

    private final OidcIdTokenVerifier delegate;

    @Autowired
    public AppleIdTokenVerifier(@Qualifier("appleRestOperations") RestOperations restOperations) {
        this(restOperations, Clock.systemUTC());
    }

    AppleIdTokenVerifier(RestOperations restOperations, Clock clock) {
        this.delegate = new OidcIdTokenVerifier(
                restOperations, clock, JWK_SET_URI, Set.of(ISSUER), SignatureAlgorithm.RS256);
    }

    /**
     * @param allowedAudiences 허용할 aud. 로그인은 Bundle ID 와 Services ID 를 모두, code 교환은
     *                         요청에 쓴 client_id 하나만 넘긴다.
     * @throws IdTokenVerificationException 검증 실패. 메시지는 로그용이며 토큰 원문을 담지 않는다.
     */
    public Jwt verify(String token, Collection<String> allowedAudiences) {
        return delegate.verify(token, allowedAudiences);
    }
}
