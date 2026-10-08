package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.infrastructure.oauth.impl.AppleIdTokenVerificationException.Kind;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Apple 이 발급한 id_token 을 검증한다. 로그인 idToken 과 code 교환 응답의 id_token 이 같은
 * 규칙·같은 JWKS 캐시를 쓰도록 한 곳에 둔다.
 *
 * <p>검증 항목: RS256 서명(Apple JWKS, kid 로 선택), iss, aud(정확히 1개이고 허용 목록 안),
 * exp 필수(clock skew 60초), iat 필수이고 미래가 아님.
 *
 * <p>iat 의 "최대 나이"는 두지 않는다. Apple 토큰의 수명(exp − iat)을 공식 문서로 확인하지 못했고,
 * 너무 짧게 잡으면 정상 로그인이 깨진다. 오래된 토큰은 exp 와 nonce 1회 소비로 막는다.
 */
@Component
public class AppleIdTokenVerifier {
    static final String ISSUER = "https://appleid.apple.com";
    static final String JWK_SET_URI = "https://appleid.apple.com/auth/keys";
    static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    // Nimbus 기본값과 같다. 명시하는 이유는 rate limit 을 켜기 위해 builder 를 직접 쓰기 때문이다.
    static final Duration JWKS_CACHE_TTL = Duration.ofMinutes(5);
    static final Duration JWKS_CACHE_REFRESH_TIMEOUT = Duration.ofSeconds(15);
    // 처음 보는 kid 마다 JWKS 를 다시 받으면, 아무 kid 나 넣은 토큰을 연사하는 것만으로
    // Apple 호출과 refresh lock 대기가 무한히 생긴다. 30초 창에 최대 2회로 묶는다.
    // trade-off: Apple 이 키를 교체한 직후 새 kid 토큰이 최대 30초 거절될 수 있다.
    static final Duration JWKS_REFRESH_MIN_INTERVAL = Duration.ofSeconds(30);

    private final JwtDecoder jwtDecoder;

    @Autowired
    public AppleIdTokenVerifier() {
        this(createRestOperations(), Clock.systemUTC());
    }

    AppleIdTokenVerifier(RestOperations restOperations, Clock clock) {
        this.jwtDecoder = createJwtDecoder(createJwkSource(restOperations), clock);
    }

    /**
     * @param allowedAudiences 허용할 aud. 로그인은 Bundle ID 와 Services ID 를 모두, code 교환은
     *                         요청에 쓴 client_id 하나만 넘긴다.
     * @throws AppleIdTokenVerificationException 검증 실패. 메시지는 로그용이며 토큰 원문을 담지 않는다.
     */
    public Jwt verify(String token, Collection<String> allowedAudiences) {
        Jwt jwt = decode(token);

        // aud 가 여러 개면 어느 것을 client_id 로 쓸지 정할 수 없고, 허용 목록 밖의 값이 섞일 수 있다.
        // Apple 은 aud 를 하나만 넣으므로 정확히 1개를 요구한다.
        List<String> audience = jwt.getAudience();
        if (audience == null || audience.size() != 1 || !allowedAudiences.contains(audience.getFirst())) {
            throw new AppleIdTokenVerificationException(Kind.INVALID, "aud is not a single allowed audience", null);
        }
        return jwt;
    }

    private Jwt decode(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (BadJwtException e) {
            // 서명/kid/iss/exp/iat 불일치. 메시지에 토큰 원문은 들어 있지 않다.
            throw new AppleIdTokenVerificationException(Kind.INVALID, e.getMessage(), e);
        } catch (JwtException e) {
            if (hasCause(e, RateLimitReachedException.class)) {
                // 캐시에 없는 kid 인데 재조회 한도를 이미 썼다. 정상 키 교체 직후가 아니면 위조 토큰이다.
                throw new AppleIdTokenVerificationException(Kind.INVALID, "unknown kid, JWKS refresh rate-limited", e);
            }
            // JWKS 조회 실패 등 토큰과 무관한 오류.
            throw new AppleIdTokenVerificationException(Kind.UNAVAILABLE, e.getMessage(), e);
        }
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static JwtDecoder createJwtDecoder(JWKSource<SecurityContext> jwkSource, Clock clock) {
        // alg 를 RS256 하나로 고정해 none / HS256(공개키를 HMAC 키로 쓰는 혼동) 을 막는다.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        // 기본값은 exp 가 없어도 통과시킨다. 만료 없는 idToken 은 받지 않는다.
        timestampValidator.setAllowEmptyExpiryClaim(false);
        timestampValidator.setClock(clock);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                // Apple 토큰에는 typ 헤더가 없다. jwt() 는 없거나 "JWT" 면 통과시킨다.
                JwtTypeValidator.jwt(),
                timestampValidator,
                issuedAtValidator(clock),
                new JwtIssuerValidator(ISSUER)
        ));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> issuedAtValidator(Clock clock) {
        return jwt -> {
            Instant issuedAt = jwt.getIssuedAt();
            if (issuedAt == null) {
                return invalid("iat claim is missing");
            }
            if (issuedAt.isAfter(clock.instant().plus(CLOCK_SKEW))) {
                return invalid("iat is in the future");
            }
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt != null && !expiresAt.isAfter(issuedAt)) {
                return invalid("exp is not after iat");
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static OAuth2TokenValidatorResult invalid(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }

    private static JWKSource<SecurityContext> createJwkSource(RestOperations restOperations) {
        // NimbusJwtDecoder.withJwkSetUri 는 rate limit 을 끈 채로(rateLimited(false)) 만들고
        // 바꿀 수 없어서 JWKSource 를 직접 만든다.
        return JWKSourceBuilder.<SecurityContext>create(jwkSetUrl(), new RestOperationsResourceRetriever(restOperations))
                .cache(JWKS_CACHE_TTL.toMillis(), JWKS_CACHE_REFRESH_TIMEOUT.toMillis())
                .rateLimited(JWKS_REFRESH_MIN_INTERVAL.toMillis())
                .build();
    }

    private static URL jwkSetUrl() {
        try {
            return URI.create(JWK_SET_URI).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("invalid JWK set URI", e);
        }
    }

    private static RestOperations createRestOperations() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(requestFactory);
    }

    /** 테스트에서 RestOperations 만 mock 하면 되도록 JWKS 요청을 RestOperations 로 보낸다. */
    private record RestOperationsResourceRetriever(RestOperations restOperations) implements ResourceRetriever {
        @Override
        public Resource retrieveResource(URL url) throws IOException {
            try {
                RequestEntity<Void> request = RequestEntity.get(url.toURI())
                        .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("application/jwk-set+json"))
                        .build();
                ResponseEntity<String> response = restOperations.exchange(request, String.class);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    throw new IOException("unexpected JWKS response: " + response.getStatusCode().value());
                }
                return new Resource(response.getBody(), MediaType.APPLICATION_JSON_VALUE);
            } catch (RestClientException | URISyntaxException e) {
                // Nimbus 는 IOException 만 조회 실패로 다룬다. 메시지에는 예외 종류만 남긴다.
                throw new IOException("JWKS request failed: " + e.getClass().getSimpleName(), e);
            }
        }
    }
}
