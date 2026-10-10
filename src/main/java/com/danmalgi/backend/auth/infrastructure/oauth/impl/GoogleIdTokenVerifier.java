package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;

import java.time.Clock;
import java.util.Collection;
import java.util.Set;

/**
 * Google 이 발급한 id_token 을 검증한다. 검증 규칙은 {@link OidcIdTokenVerifier} 에 있고
 * 여기에는 Google 고유 값만 둔다.
 *
 * <p><b>iss 를 두 개 허용하는 이유</b>: Google 은 같은 뜻으로 스킴이 있는 형태와 없는 형태를
 * 모두 발급해 왔다. 하나만 받으면 다른 형태를 쓰는 클라이언트가 전부 거절된다.
 *
 * <p><b>alg 를 RS256 하나로 고정하는 이유</b>: 오늘 Google JWKS 에는 RS256 키만 있다.
 * 쓰지 않는 알고리즘을 미리 열어 두지 않는다. 다만 Google 이 서명 알고리즘을 바꾸면
 * <b>Google 로그인이 전부 막힌다</b> — 그때는 아래 생성자에 알고리즘을 추가하면 된다.
 *
 * <p>미완성: Google 은 nonce 를 쓰지 않으므로 유출된 id_token 을 exp 전까지 재사용할 수 있다.
 * Apple 과 달리 1회 소비 장치가 없다 (docs/02-domain/auth-and-identity.md 의 빈틈 참고).
 */
@Component
public class GoogleIdTokenVerifier {
    static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final OidcIdTokenVerifier delegate;

    @Autowired
    public GoogleIdTokenVerifier() {
        this(OidcIdTokenVerifier.createRestOperations(), Clock.systemUTC());
    }

    GoogleIdTokenVerifier(RestOperations restOperations, Clock clock) {
        this.delegate = new OidcIdTokenVerifier(
                restOperations, clock, JWK_SET_URI, ISSUERS, SignatureAlgorithm.RS256);
    }

    /**
     * @param allowedAudiences 허용할 aud. 플랫폼마다 client id 가 다를 수 있어 목록으로 받는다.
     * @throws IdTokenVerificationException 검증 실패. 메시지는 로그용이며 토큰 원문을 담지 않는다.
     */
    public Jwt verify(String token, Collection<String> allowedAudiences) {
        return delegate.verify(token, allowedAudiences);
    }
}
