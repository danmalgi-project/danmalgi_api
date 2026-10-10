package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.infrastructure.oauth.impl.IdTokenVerificationException.Kind;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
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
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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
import java.util.Set;

/**
 * OIDC 제공자가 발급한 id_token 을 검증한다. Apple / Google 이 같은 규칙으로 검증하도록
 * 제공자와 무관한 부분을 여기 모았다. 제공자별로 다른 것은 <b>JWKS URI, 허용 iss, 허용 alg</b>
 * 셋뿐이고, 나머지(캐시·rate limit·시각 검사·aud 규칙·예외 매핑)는 전부 공통이다.
 *
 * <p>검증 항목: 지정한 alg 의 서명(제공자 JWKS, kid 로 선택), iss(허용 목록),
 * aud(정확히 1개이고 허용 목록 안), exp 필수(clock skew 60초), iat 필수이고 미래가 아님.
 *
 * <p>iat 의 "최대 나이"는 두지 않는다. 토큰 수명(exp − iat)을 제공자 공식 문서로 확인하지 못했고,
 * 너무 짧게 잡으면 정상 로그인이 깨진다. 오래된 토큰은 exp 로 막는다.
 *
 * <p>이 클래스를 고치면 Apple 과 Google 이 <b>함께</b> 바뀐다. 한쪽만 바꿔야 하는 정책은
 * 각 제공자 verifier 나 어댑터에 둔다.
 */
final class OidcIdTokenVerifier {
    static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    // Nimbus 기본값과 같다. 명시하는 이유는 rate limit 을 켜기 위해 builder 를 직접 쓰기 때문이다.
    static final Duration JWKS_CACHE_TTL = Duration.ofMinutes(5);
    static final Duration JWKS_CACHE_REFRESH_TIMEOUT = Duration.ofSeconds(15);
    // 처음 보는 kid 마다 JWKS 를 다시 받으면, 아무 kid 나 넣은 토큰을 연사하는 것만으로
    // 제공자 호출과 refresh lock 대기가 무한히 생긴다. 30초 창에 최대 2회로 묶는다.
    // trade-off: 제공자가 키를 교체한 직후 새 kid 토큰이 최대 30초 거절될 수 있다.
    static final Duration JWKS_REFRESH_MIN_INTERVAL = Duration.ofSeconds(30);

    private final JwtDecoder jwtDecoder;

    /**
     * @param allowedIssuers 허용할 iss. Apple 은 1개, Google 은 스킴 있는/없는 두 형태를 모두 쓴다.
     * @param algorithms     허용할 서명 알고리즘. 하나로 고정해 alg 혼동을 막는 것이 목적이라
     *                       제공자가 실제로 쓰는 것만 넘긴다.
     */
    OidcIdTokenVerifier(
            RestOperations restOperations,
            Clock clock,
            String jwkSetUri,
            Set<String> allowedIssuers,
            SignatureAlgorithm... algorithms
    ) {
        this.jwtDecoder = createJwtDecoder(
                createJwkSource(restOperations, jwkSetUri), clock, allowedIssuers, algorithms);
    }

    /**
     * @param allowedAudiences 허용할 aud. 로그인은 플랫폼별 client id 를 모두, Apple code 교환은
     *                         요청에 쓴 client_id 하나만 넘긴다.
     * @throws IdTokenVerificationException 검증 실패. 메시지는 로그용이며 토큰 원문을 담지 않는다.
     */
    Jwt verify(String token, Collection<String> allowedAudiences) {
        Jwt jwt = decode(token);

        // aud 가 여러 개면 어느 것을 client_id 로 쓸지 정할 수 없고, 허용 목록 밖의 값이 섞일 수 있다.
        // Apple/Google 모두 aud 를 하나만 넣으므로 정확히 1개를 요구한다.
        List<String> audience = jwt.getAudience();
        if (audience == null || audience.size() != 1 || !allowedAudiences.contains(audience.getFirst())) {
            // 받은 aud 를 메시지에 담는다. client id 는 앱 바이너리에 들어가는 공개 식별자이지
            // 비밀이 아니고, aud 설정이 틀리면 해당 제공자 로그인이 전부 죽는데 이 값이 없으면
            // 로그만 보고는 원인을 알 수 없다. 토큰 원문·서명·email 은 여전히 남기지 않는다.
            throw new IdTokenVerificationException(
                    Kind.INVALID, "aud is not a single allowed audience: " + audience, null);
        }
        return jwt;
    }

    private Jwt decode(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (BadJwtException e) {
            // 서명/kid/iss/exp/iat 불일치. 메시지에 토큰 원문은 들어 있지 않다.
            throw new IdTokenVerificationException(Kind.INVALID, e.getMessage(), e);
        } catch (JwtException e) {
            if (hasCause(e, RateLimitReachedException.class)) {
                // 캐시에 없는 kid 인데 재조회 한도를 이미 썼다. 정상 키 교체 직후가 아니면 위조 토큰이다.
                throw new IdTokenVerificationException(Kind.INVALID, "unknown kid, JWKS refresh rate-limited", e);
            }
            // JWKS 조회 실패 등 토큰과 무관한 오류.
            throw new IdTokenVerificationException(Kind.UNAVAILABLE, e.getMessage(), e);
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

    private static JwtDecoder createJwtDecoder(
            JWKSource<SecurityContext> jwkSource,
            Clock clock,
            Set<String> allowedIssuers,
            SignatureAlgorithm... algorithms
    ) {
        // alg 를 허용 목록으로 고정해 none / HS256(공개키를 HMAC 키로 쓰는 혼동) 을 막는다.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource)
                .jwsAlgorithms(supported -> supported.addAll(List.of(algorithms)))
                .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        // 기본값은 exp 가 없어도 통과시킨다. 만료 없는 idToken 은 받지 않는다.
        timestampValidator.setAllowEmptyExpiryClaim(false);
        timestampValidator.setClock(clock);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                // Apple/Google 토큰에는 typ 헤더가 없다. jwt() 는 없거나 "JWT" 면 통과시킨다.
                JwtTypeValidator.jwt(),
                timestampValidator,
                issuedAtValidator(clock),
                issuerValidator(allowedIssuers)
        ));
        return decoder;
    }

    /**
     * {@code JwtIssuerValidator} 는 issuer 를 하나만 받는데 Google 은 스킴 있는/없는 두 형태를
     * 모두 쓰므로 클레임 검사로 대신한다.
     *
     * <p>{@code toString()} 으로 비교하는 이유: Spring 의 기본 클레임 변환기는 iss 를 URL 로
     * 바꿔 보고 실패하면 String 으로 그대로 둔다. 즉 {@code "accounts.google.com"} 은 String,
     * {@code "https://accounts.google.com"} 은 URL 로 들어오고, 후자의 {@code toString()} 은
     * 트레일링 슬래시 없는 원문과 같다.
     */
    private static OAuth2TokenValidator<Jwt> issuerValidator(Set<String> allowedIssuers) {
        return new JwtClaimValidator<Object>(
                JwtClaimNames.ISS, issuer -> issuer != null && allowedIssuers.contains(issuer.toString()));
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

    private static JWKSource<SecurityContext> createJwkSource(RestOperations restOperations, String jwkSetUri) {
        // NimbusJwtDecoder.withJwkSetUri 는 rate limit 을 끈 채로(rateLimited(false)) 만들고
        // 바꿀 수 없어서 JWKSource 를 직접 만든다.
        return JWKSourceBuilder
                .<SecurityContext>create(jwkSetUrl(jwkSetUri), new RestOperationsResourceRetriever(restOperations))
                .cache(JWKS_CACHE_TTL.toMillis(), JWKS_CACHE_REFRESH_TIMEOUT.toMillis())
                .rateLimited(JWKS_REFRESH_MIN_INTERVAL.toMillis())
                .build();
    }

    private static URL jwkSetUrl(String jwkSetUri) {
        try {
            return URI.create(jwkSetUri).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("invalid JWK set URI: " + jwkSetUri, e);
        }
    }

    static RestOperations createRestOperations() {
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
