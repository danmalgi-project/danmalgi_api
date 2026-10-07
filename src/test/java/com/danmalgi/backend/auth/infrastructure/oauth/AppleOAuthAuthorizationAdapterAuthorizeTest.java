package com.danmalgi.backend.auth.infrastructure.oauth;

import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.oauth.impl.AppleOAuthAuthorizationAdapter;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestOperations;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppleOAuthAuthorizationAdapterAuthorizeTest {

    private static final String ISSUER = "https://appleid.apple.com";
    private static final String IOS_AUD = "com.danmalgi.mobile";
    private static final String ANDROID_AUD = "com.danmalgi.mobile.service";
    private static final String APPLE_SUB = "001234.0123456789abcdef0123456789abcdef.0123";
    private static final String RAW_NONCE = "Xk3v9QpL2mN8rT5wY1zA7bC4dE6fG0hJ";

    private static RSAKey appleKey;
    private static RSAKey rotatedKey;
    private static RSAKey attackerKeyWithSameKid;

    private RestOperations restOperations;
    private AppleOAuthAuthorizationAdapter adapter;

    @BeforeAll
    static void generateKeys() throws Exception {
        appleKey = new RSAKeyGenerator(2048).keyID("apple-kid-1").generate();
        rotatedKey = new RSAKeyGenerator(2048).keyID("apple-kid-2").generate();
        attackerKeyWithSameKid = new RSAKeyGenerator(2048).keyID("apple-kid-1").generate();
    }

    @BeforeEach
    void setUp() throws Exception {
        restOperations = mock(RestOperations.class);
        givenJwks(new JWKSet(appleKey.toPublicJWK()));
        adapter = newAdapter(restOperations, List.of(IOS_AUD, ANDROID_AUD));
    }

    @Test
    void authorize_iOS_aud_토큰이면_APPLE_PendingOAuthProfile_반환() throws Exception {
        PendingOAuthProfile result = authorizeWithoutNonce(sign(appleKey, claims -> claims.audience(IOS_AUD)));

        assertThat(result.getIdentifyId()).isEqualTo(APPLE_SUB);
        assertThat(result.getEmail()).isEqualTo("abc123@privaterelay.appleid.com");
        assertThat(result.getOauthType()).isEqualTo(OauthType.APPLE);
        assertThat(result.getUserId()).isNull();
    }

    @Test
    void authorize_Android_Services_ID_aud_토큰이면_통과() throws Exception {
        PendingOAuthProfile result = authorizeWithoutNonce(sign(appleKey, claims -> claims.audience(ANDROID_AUD)));

        assertThat(result.getIdentifyId()).isEqualTo(APPLE_SUB);
    }

    @Test
    void authorize_검증된_aud를_oauthClientId로_반환한다() throws Exception {
        // code 교환의 client_id 는 code 를 발급받은 클라이언트여야 한다.
        assertThat(authorizeWithoutNonce(sign(appleKey, claims -> claims.audience(IOS_AUD))).getOauthClientId())
                .isEqualTo(IOS_AUD);
        assertThat(authorizeWithoutNonce(sign(appleKey, claims -> claims.audience(ANDROID_AUD))).getOauthClientId())
                .isEqualTo(ANDROID_AUD);
    }

    @Test
    void authorize_email_verified와_is_private_email이_문자열이든_boolean이든_통과() throws Exception {
        String stringClaims = sign(appleKey, claims -> claims
                .claim("email_verified", "true").claim("is_private_email", "true"));
        String booleanClaims = sign(appleKey, claims -> claims
                .claim("email_verified", true).claim("is_private_email", false));

        assertThat(authorizeWithoutNonce(stringClaims).getIdentifyId()).isEqualTo(APPLE_SUB);
        assertThat(authorizeWithoutNonce(booleanClaims).getIdentifyId()).isEqualTo(APPLE_SUB);
    }

    @Test
    void authorize_raw_nonce가_없으면_nonce_클레임을_검증하지_않는다() throws Exception {
        // 구 앱 호환을 위한 단계적 배포 동작이다. 필수로 바꾸면 이 테스트를 뒤집는다.
        String idToken = sign(appleKey, claims -> claims.claim("nonce", "anything"));

        assertThat(authorizeWithoutNonce(idToken).getIdentifyId()).isEqualTo(APPLE_SUB);
    }

    @Test
    void authorize_raw_nonce의_sha256_hex가_nonce_클레임과_같으면_통과() throws Exception {
        String idToken = sign(appleKey, claims -> claims.claim("nonce", sha256LowerHex(RAW_NONCE)));

        PendingOAuthProfile result = adapter.authorize(new OAuthCredential(idToken, RAW_NONCE, null));

        assertThat(result.getIdentifyId()).isEqualTo(APPLE_SUB);
    }

    @Test
    void authorize_nonce가_불일치하면_UNAUTHENTICATED() throws Exception {
        String idToken = sign(appleKey, claims -> claims.claim("nonce", sha256LowerHex("other-nonce")));

        assertRejected(new OAuthCredential(idToken, RAW_NONCE, null));
    }

    @Test
    void authorize_raw_nonce가_있는데_nonce_클레임이_없으면_UNAUTHENTICATED() throws Exception {
        assertRejected(new OAuthCredential(sign(appleKey, claims -> {}), RAW_NONCE, null));
    }

    @Test
    void authorize_nonce_클레임에_raw_nonce_원문이_들어있으면_UNAUTHENTICATED() throws Exception {
        // 앱이 해시하지 않고 원문을 Apple 에 넘긴 경우. 원문 그대로 비교해 통과시키면 안 된다.
        String idToken = sign(appleKey, claims -> claims.claim("nonce", RAW_NONCE));

        assertRejected(new OAuthCredential(idToken, RAW_NONCE, null));
    }

    @Test
    void authorize_nonce_클레임이_대문자_hex면_UNAUTHENTICATED() throws Exception {
        String idToken = sign(appleKey, claims -> claims.claim("nonce", sha256LowerHex(RAW_NONCE).toUpperCase()));

        assertRejected(new OAuthCredential(idToken, RAW_NONCE, null));
    }

    @Test
    void authorize_clock_skew_범위_안에서_만료된_토큰은_통과() throws Exception {
        String idToken = sign(appleKey, claims -> claims
                .issueTime(secondsFromNow(-600)).expirationTime(secondsFromNow(-30)));

        assertThat(authorizeWithoutNonce(idToken).getIdentifyId()).isEqualTo(APPLE_SUB);
    }

    @Test
    void authorize_aud가_허용목록에_없으면_UNAUTHENTICATED() throws Exception {
        assertRejected(sign(appleKey, claims -> claims.audience("com.other.app")));
    }

    @Test
    void authorize_만료된_토큰이면_UNAUTHENTICATED() throws Exception {
        assertRejected(sign(appleKey, claims -> claims
                .issueTime(secondsFromNow(-3600)).expirationTime(secondsFromNow(-600))));
    }

    @Test
    void authorize_iss가_다르면_UNAUTHENTICATED() throws Exception {
        assertRejected(sign(appleKey, claims -> claims.issuer("https://accounts.google.com")));
    }

    @Test
    void authorize_같은_kid의_다른_키로_서명했으면_UNAUTHENTICATED() throws Exception {
        assertRejected(sign(attackerKeyWithSameKid, claims -> {}));
    }

    @Test
    void authorize_HS256_토큰이면_UNAUTHENTICATED() throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(appleKey.getKeyID()).build();
        SignedJWT jwt = new SignedJWT(header, validClaims().build());
        jwt.sign(new MACSigner("hs256-secret-for-alg-confusion-test!!".getBytes()));

        assertRejected(jwt.serialize());
    }

    @Test
    void authorize_알수없는_kid면_JWKS를_다시_받아본_뒤_UNAUTHENTICATED() throws Exception {
        assertRejected(sign(rotatedKey, claims -> {}));

        // 캐시에 없는 kid 라 1회 + 재조회 1회
        verify(restOperations, times(2)).exchange(any(RequestEntity.class), eq(String.class));
    }

    @Test
    void authorize_Apple이_키를_교체하면_JWKS를_다시_받아_통과() throws Exception {
        when(restOperations.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(new JWKSet(appleKey.toPublicJWK()).toString()))
                .thenReturn(ResponseEntity.ok(
                        new JWKSet(List.of(appleKey.toPublicJWK(), rotatedKey.toPublicJWK())).toString()));

        PendingOAuthProfile result = authorizeWithoutNonce(sign(rotatedKey, claims -> {}));

        assertThat(result.getIdentifyId()).isEqualTo(APPLE_SUB);
        verify(restOperations, times(2)).exchange(any(RequestEntity.class), eq(String.class));
    }

    @Test
    void authorize_JWKS는_캐싱되어_연속_요청에_한번만_받는다() throws Exception {
        authorizeWithoutNonce(sign(appleKey, claims -> {}));
        authorizeWithoutNonce(sign(appleKey, claims -> claims.audience(ANDROID_AUD)));

        verify(restOperations, times(1)).exchange(any(RequestEntity.class), eq(String.class));
    }

    @Test
    void authorize_JWKS_조회에_실패하면_UNAUTHENTICATED() throws Exception {
        when(restOperations.exchange(any(RequestEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));

        assertRejected(sign(appleKey, claims -> {}));
    }

    @Test
    void authorize_sub가_없으면_UNAUTHENTICATED() throws Exception {
        assertRejected(sign(appleKey, claims -> claims.subject(null)));
    }

    @Test
    void authorize_email이_없으면_UNAUTHENTICATED() throws Exception {
        assertRejected(sign(appleKey, claims -> claims.claim("email", null)));
    }

    @Test
    void authorize_JWT_형식이_아니면_UNAUTHENTICATED() {
        assertRejected("not-a-jwt");
    }

    @Test
    void 생성자_aud_허용목록이_비어있으면_기동_실패() {
        assertThatThrownBy(() -> newAdapter(restOperations, List.of(" ")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    private PendingOAuthProfile authorizeWithoutNonce(String idToken) {
        return adapter.authorize(new OAuthCredential(idToken, null, null));
    }

    private void assertRejected(String idToken) {
        assertRejected(new OAuthCredential(idToken, null, null));
    }

    private void assertRejected(OAuthCredential credential) {
        assertThatThrownBy(() -> adapter.authorize(credential))
                .isInstanceOfSatisfying(OauthAuthorizeFailException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(Status.UNAUTHENTICATED);
                    // 거절 이유는 클라이언트에 노출하지 않는다.
                    assertThat(e.toDescription()).isEqualTo("Invalid Apple idToken");
                });
    }

    private void givenJwks(JWKSet jwkSet) {
        when(restOperations.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jwkSet.toString()));
    }

    private static JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(IOS_AUD)
                .subject(APPLE_SUB)
                .issueTime(secondsFromNow(0))
                .expirationTime(secondsFromNow(600))
                .claim("email", "abc123@privaterelay.appleid.com")
                .claim("email_verified", true)
                .claim("is_private_email", true);
    }

    /** Apple idToken 과 같은 헤더(alg=RS256, kid, typ 없음)로 서명한다. */
    private static String sign(RSAKey key, Consumer<JWTClaimsSet.Builder> customizer) throws Exception {
        JWTClaimsSet.Builder claims = validClaims();
        customizer.accept(claims);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    // 운영 코드의 헬퍼를 쓰지 않고 따로 계산해 같은 실수를 공유하지 않게 한다.
    // sign() 의 customizer 람다 안에서 부르므로 checked 예외를 던지지 않는다.
    private static String sha256LowerHex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Date secondsFromNow(long seconds) {
        return Date.from(Instant.now().plusSeconds(seconds));
    }

    // 운영 생성자는 실제 Apple JWKS 로 요청하므로, 같은 decoder 구성에 mock RestOperations 만 주입한다.
    private static AppleOAuthAuthorizationAdapter newAdapter(RestOperations restOperations, List<String> audiences)
            throws Exception {
        Method factory = AppleOAuthAuthorizationAdapter.class
                .getDeclaredMethod("createJwtDecoder", RestOperations.class, List.class);
        factory.setAccessible(true);
        Constructor<AppleOAuthAuthorizationAdapter> constructor = AppleOAuthAuthorizationAdapter.class
                .getDeclaredConstructor(org.springframework.security.oauth2.jwt.JwtDecoder.class);
        constructor.setAccessible(true);
        return constructor.newInstance(factory.invoke(null, restOperations, audiences));
    }
}
