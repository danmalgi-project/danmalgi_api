package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture;
import com.danmalgi.backend.auth.infrastructure.oauth.impl.IdTokenVerificationException.Kind;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.GOOGLE_SUB;
import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.IOS_AUD;
import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.ISSUER_BARE;
import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.WEB_AUD;
import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.generateKey;
import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.validClaims;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleIdTokenVerifierVerifyTest {

    // JWT 시각은 초 단위라 기준 시각도 초로 자른다.
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Set<String> AUDIENCES = Set.of(WEB_AUD, IOS_AUD);

    private static RSAKey googleKey;
    private static RSAKey rotatedKey;
    private static RSAKey attackerKeyWithSameKid;

    private RestOperations restOperations;
    private GoogleIdTokenVerifier verifier;

    @BeforeAll
    static void generateKeys() {
        googleKey = generateKey("google-kid-1");
        rotatedKey = generateKey("google-kid-2");
        attackerKeyWithSameKid = generateKey("google-kid-1");
    }

    @BeforeEach
    void setUp() {
        restOperations = mock(RestOperations.class);
        givenJwks(new JWKSet(googleKey.toPublicJWK()));
        verifier = new GoogleIdTokenVerifier(restOperations, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void verify_정상_Google_토큰이면_Jwt를_반환한다() {
        Jwt jwt = verifier.verify(sign(googleKey, claims -> {}), AUDIENCES);

        assertThat(jwt.getSubject()).isEqualTo(GOOGLE_SUB);
        assertThat(jwt.getAudience()).containsExactly(WEB_AUD);
    }

    @Test
    void verify_iss가_스킴없는_accounts_google_com이어도_통과한다() {
        // Google 은 같은 뜻으로 두 형태를 발급한다. 하나만 받으면 절반이 거절된다.
        String token = sign(googleKey, claims -> claims.issuer(ISSUER_BARE));

        assertThat(verifier.verify(token, AUDIENCES).getSubject()).isEqualTo(GOOGLE_SUB);
    }

    @Test
    void verify_iss가_다른_제공자면_INVALID() {
        assertInvalid(sign(googleKey, claims -> claims.issuer("https://appleid.apple.com")));
    }

    @Test
    void verify_iss가_없으면_INVALID() {
        assertInvalid(sign(googleKey, claims -> claims.issuer(null)));
    }

    @Test
    void verify_alg_none이면_INVALID() {
        String token = new PlainJWT(validClaims(NOW).build()).serialize();

        assertInvalid(token);
    }

    @Test
    void verify_HS256이면_INVALID() throws Exception {
        // 공개키를 HMAC 키로 쓰게 만드는 alg 혼동 공격.
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(googleKey.getKeyID()).build(),
                validClaims(NOW).build());
        jwt.sign(new MACSigner("hs256-secret-for-alg-confusion-test!!".getBytes()));

        assertInvalid(jwt.serialize());
    }

    @Test
    void verify_같은_kid의_다른_키로_서명했으면_INVALID() {
        assertInvalid(sign(attackerKeyWithSameKid, claims -> {}));
    }

    @Test
    void verify_aud가_허용목록_밖이면_INVALID() {
        assertInvalid(sign(googleKey, claims -> claims.audience("999-other.apps.googleusercontent.com")));
    }

    @Test
    void verify_aud가_여러개면_허용값이_섞여있어도_INVALID() {
        assertInvalid(sign(googleKey, claims -> claims.audience(List.of(WEB_AUD, "999-other.apps.googleusercontent.com"))));
    }

    @Test
    void verify_aud는_호출부가_넘긴_목록으로만_판단한다() {
        String iosToken = sign(googleKey, claims -> claims.audience(IOS_AUD));

        assertThatThrownBy(() -> verifier.verify(iosToken, Set.of(WEB_AUD)))
                .isInstanceOfSatisfying(IdTokenVerificationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.INVALID));
    }

    @Test
    void verify_aud가_틀리면_받은_aud를_메시지에_남긴다() {
        // 설정 사고를 로그만 보고 진단할 수 있어야 한다. client id 는 공개 식별자다.
        String token = sign(googleKey, claims -> claims.audience("999-other.apps.googleusercontent.com"));

        assertThatThrownBy(() -> verifier.verify(token, AUDIENCES))
                .isInstanceOf(IdTokenVerificationException.class)
                .hasMessageContaining("999-other.apps.googleusercontent.com");
    }

    @Test
    void verify_exp가_없으면_INVALID() {
        assertInvalid(sign(googleKey, claims -> claims.expirationTime(null)));
    }

    @Test
    void verify_iat가_없으면_INVALID() {
        assertInvalid(sign(googleKey, claims -> claims.issueTime(null)));
    }

    @Test
    void verify_iat가_clock_skew보다_미래면_INVALID() {
        assertInvalid(sign(googleKey, claims -> claims
                .issueTime(at(120)).expirationTime(at(3720))));
    }

    @Test
    void verify_exp가_iat보다_앞서면_INVALID() {
        assertInvalid(sign(googleKey, claims -> claims
                .issueTime(at(50)).expirationTime(at(40))));
    }

    @Test
    void verify_clock_skew_범위_안에서_만료된_토큰은_통과() {
        String token = sign(googleKey, claims -> claims.issueTime(at(-3600)).expirationTime(at(-30)));

        assertThat(verifier.verify(token, AUDIENCES).getSubject()).isEqualTo(GOOGLE_SUB);
    }

    @Test
    void verify_clock_skew를_넘겨_만료된_토큰은_INVALID() {
        assertInvalid(sign(googleKey, claims -> claims.issueTime(at(-7200)).expirationTime(at(-120))));
    }

    @Test
    void verify_JWT_형식이_아니면_INVALID() {
        assertInvalid("not-a-jwt");
    }

    @Test
    void verify_JWKS는_캐싱되어_연속_요청에_한번만_받는다() {
        verifier.verify(sign(googleKey, claims -> {}), AUDIENCES);
        verifier.verify(sign(googleKey, claims -> claims.audience(IOS_AUD)), AUDIENCES);

        verify(restOperations, times(1)).exchange(any(RequestEntity.class), eq(String.class));
    }

    @Test
    void verify_알수없는_kid면_JWKS를_한번_다시_받아본_뒤_INVALID() {
        assertInvalid(sign(rotatedKey, claims -> {}));

        // 처음 받기 1회 + 캐시에 없는 kid 라 재조회 1회
        verify(restOperations, times(2)).exchange(any(RequestEntity.class), eq(String.class));
    }

    @Test
    void verify_Google이_키를_교체하면_JWKS를_다시_받아_통과() {
        when(restOperations.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(new JWKSet(googleKey.toPublicJWK()).toString()))
                .thenReturn(ResponseEntity.ok(
                        new JWKSet(List.of(googleKey.toPublicJWK(), rotatedKey.toPublicJWK())).toString()));

        assertThat(verifier.verify(sign(rotatedKey, claims -> {}), AUDIENCES).getSubject()).isEqualTo(GOOGLE_SUB);
        verify(restOperations, times(2)).exchange(any(RequestEntity.class), eq(String.class));
    }

    @Test
    void verify_알수없는_kid를_연달아_보내도_rate_limit_안에서는_더_받지_않는다() {
        String unknownKidToken = sign(rotatedKey, claims -> {});

        assertInvalid(unknownKidToken);
        assertInvalid(unknownKidToken);
        assertInvalid(unknownKidToken);

        // 한도에 걸린 요청은 Google 장애가 아니라 위조 토큰으로 본다(INVALID). 호출은 늘지 않는다.
        verify(restOperations, times(2)).exchange(any(RequestEntity.class), eq(String.class));
    }

    @Test
    void verify_JWKS_조회에_실패하면_UNAVAILABLE() {
        when(restOperations.exchange(any(RequestEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));

        assertThatThrownBy(() -> verifier.verify(sign(googleKey, claims -> {}), AUDIENCES))
                .isInstanceOfSatisfying(IdTokenVerificationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> verifier.verify(token, AUDIENCES))
                .isInstanceOfSatisfying(IdTokenVerificationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.INVALID));
    }

    private void givenJwks(JWKSet jwkSet) {
        when(restOperations.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jwkSet.toString()));
    }

    private static String sign(RSAKey key, Consumer<JWTClaimsSet.Builder> customizer) {
        return GoogleIdTokenFixture.sign(key, NOW, customizer);
    }

    private static Date at(long secondsFromNow) {
        return Date.from(NOW.plusSeconds(secondsFromNow));
    }
}
