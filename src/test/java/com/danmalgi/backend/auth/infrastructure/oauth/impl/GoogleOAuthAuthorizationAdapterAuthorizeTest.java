package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.exception.OauthProviderUnavailableException;
import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestOperations;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.EMAIL;
import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.GOOGLE_SUB;
import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.IOS_AUD;
import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.WEB_AUD;
import static com.danmalgi.backend.auth.fixture.GoogleIdTokenFixture.generateKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleOAuthAuthorizationAdapterAuthorizeTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private static RSAKey googleKey;

    private RestOperations restOperations;
    private GoogleOAuthAuthorizationAdapter adapter;

    @BeforeAll
    static void generateKeys() {
        googleKey = generateKey("google-kid-1");
    }

    @BeforeEach
    void setUp() {
        restOperations = mock(RestOperations.class);
        when(restOperations.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(new JWKSet(googleKey.toPublicJWK()).toString()));
        adapter = newAdapter();
    }

    @Test
    void authorize_정상_idToken으로_GOOGLE_PendingOAuthProfile을_반환한다() {
        PendingOAuthProfile result = authorize(sign(claims -> {}));

        assertThat(result.getIdentifyId()).isEqualTo(GOOGLE_SUB);
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getOauthType()).isEqualTo(OauthType.GOOGLE);
        // userId 는 AuthService 가 users 시퀀스에서 확보해 채운다.
        assertThat(result.getUserId()).isNull();
        // Google picture 클레임을 읽지 않으므로 Authorization 경로에서는 항상 null 이다.
        assertThat(result.getProfileImageUrl()).isNull();
        // code 교환 포트가 없어 읽는 쪽이 없다. 채우면 쓰이는 값처럼 보인다.
        assertThat(result.getOauthClientId()).isNull();
    }

    @Test
    void authorize_서명이_위조됐으면_UNAUTHENTICATED() {
        // 서명을 검증하지 않던 시절에는 이 토큰으로 아무 계정이나 만들 수 있었다.
        RSAKey attackerKey = generateKey("google-kid-1");

        assertRejected(GoogleIdTokenFixture.sign(attackerKey, NOW, claims -> {}));
    }

    @Test
    void authorize_aud가_설정된_허용목록_밖이면_UNAUTHENTICATED() {
        assertRejected(sign(claims -> claims.audience("999-other.apps.googleusercontent.com")));
    }

    @Test
    void authorize_iss가_다른_제공자면_UNAUTHENTICATED() {
        assertRejected(sign(claims -> claims.issuer("https://appleid.apple.com")));
    }

    @Test
    void authorize_만료된_토큰이면_UNAUTHENTICATED() {
        assertRejected(sign(claims -> claims
                .issueTime(at(-7200)).expirationTime(at(-120))));
    }

    @Test
    void authorize_sub가_없으면_UNAUTHENTICATED() {
        assertRejected(sign(claims -> claims.subject(null)));
    }

    @Test
    void authorize_email이_없으면_UNAUTHENTICATED() {
        // UserEntity.email 이 @NotNull 이라 email 검증은 유지한다.
        assertRejected(sign(claims -> claims.claim("email", null)));
    }

    @Test
    void authorize_name_클레임이_없어도_정상_반환한다() {
        // name 은 profile 스코프가 있어야 실린다. normalizeName 제거로 소비자가 0개가 됐으므로
        // 쓰지도 않는 필드가 없다는 이유로 로그인을 거부하지 않는다 (이슈 #37 요구사항 3).
        String idToken = sign(claims -> claims.claim("name", null));

        assertThatCode(() -> authorize(idToken)).doesNotThrowAnyException();
    }

    @Test
    void authorize_JWT_형식이_아니면_UNAUTHENTICATED() {
        assertRejected("not.a.valid.jwt.token");
    }

    @Test
    void authorize_JWKS_조회에_실패하면_재시도를_뜻하는_UNAVAILABLE() {
        when(restOperations.exchange(any(RequestEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));
        String idToken = sign(claims -> {});

        assertThatThrownBy(() -> authorize(idToken))
                .isInstanceOfSatisfying(OauthProviderUnavailableException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(Status.UNAVAILABLE));
    }

    @Test
    void authorize_raw_nonce가_있어도_검증하지_않는다() {
        // nonce 는 Apple 전용이다. Google 토큰에 nonce 클레임이 없어도 거절하지 않는다.
        PendingOAuthProfile result = adapter.authorize(new OAuthCredential(sign(claims -> {}), "raw-nonce", null));

        assertThat(result.getIdentifyId()).isEqualTo(GOOGLE_SUB);
    }

    @Test
    void authorize_어댑터가_name과_tag를_생성하지_않는다() {
        // 해시 기반 tag 가 uk_users_name_tag 를 선점해 신규 로그인이 실패한 것이 이슈 #37 의
        // 직접 원인이다. 두 헬퍼가 되살아나면 같은 회귀가 재발한다.
        List<String> declaredMethods = Arrays.stream(GoogleOAuthAuthorizationAdapter.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(declaredMethods).doesNotContain("normalizeName", "generateTag");
    }

    @Test
    void 생성자_aud_허용목록이_비어있으면_기동_실패() {
        // 설정을 빠뜨린 채 검증이 꺼져서 뜨는 것이 안 뜨는 것보다 나쁘다.
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(restOperations, Clock.systemUTC());

        assertThatThrownBy(() -> new GoogleOAuthAuthorizationAdapter(verifier, List.of(" ")))
                .isInstanceOf(IllegalStateException.class);
    }

    private GoogleOAuthAuthorizationAdapter newAdapter() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        // 운영 생성자는 실제 Google JWKS 로 요청하므로 mock RestOperations 를 쓰는 verifier 를 주입한다.
        return new GoogleOAuthAuthorizationAdapter(
                new GoogleIdTokenVerifier(restOperations, clock),
                List.of(WEB_AUD, " " + IOS_AUD + " "));
    }

    private PendingOAuthProfile authorize(String idToken) {
        return adapter.authorize(new OAuthCredential(idToken, null, null));
    }

    private void assertRejected(String idToken) {
        assertThatThrownBy(() -> authorize(idToken))
                .isInstanceOfSatisfying(OauthAuthorizeFailException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(Status.UNAUTHENTICATED);
                    // 거절 사유는 클라이언트에 노출하지 않는다.
                    assertThat(e.toDescription()).isEqualTo("Invalid Google idToken");
                });
    }

    private static String sign(Consumer<JWTClaimsSet.Builder> customizer) {
        return GoogleIdTokenFixture.sign(googleKey, NOW, customizer);
    }

    private static Date at(long secondsFromNow) {
        return Date.from(NOW.plusSeconds(secondsFromNow));
    }
}
