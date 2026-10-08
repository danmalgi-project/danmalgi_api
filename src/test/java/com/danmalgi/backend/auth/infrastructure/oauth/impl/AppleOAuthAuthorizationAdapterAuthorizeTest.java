package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.exception.OauthProviderUnavailableException;
import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.fixture.AppleIdTokenFixture;
import com.danmalgi.backend.auth.infrastructure.replay.OAuthNonceReplayStore;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;

import static com.danmalgi.backend.auth.fixture.AppleIdTokenFixture.ANDROID_AUD;
import static com.danmalgi.backend.auth.fixture.AppleIdTokenFixture.APPLE_SUB;
import static com.danmalgi.backend.auth.fixture.AppleIdTokenFixture.EMAIL;
import static com.danmalgi.backend.auth.fixture.AppleIdTokenFixture.IOS_AUD;
import static com.danmalgi.backend.auth.fixture.AppleIdTokenFixture.generateKey;
import static com.danmalgi.backend.auth.fixture.AppleIdTokenFixture.sha256LowerHex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppleOAuthAuthorizationAdapterAuthorizeTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final String RAW_NONCE = "Xk3v9QpL2mN8rT5wY1zA7bC4dE6fG0hJ";
    private static final String NONCE_CLAIM = sha256LowerHex(RAW_NONCE);

    private static RSAKey appleKey;

    private RestOperations restOperations;
    private OAuthNonceReplayStore nonceReplayStore;
    private AppleOAuthAuthorizationAdapter adapter;

    @BeforeAll
    static void generateKeys() {
        appleKey = generateKey("apple-kid-1");
    }

    @BeforeEach
    void setUp() {
        restOperations = mock(RestOperations.class);
        when(restOperations.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(new JWKSet(appleKey.toPublicJWK()).toString()));
        nonceReplayStore = mock(OAuthNonceReplayStore.class);
        when(nonceReplayStore.markUsed(eq(OauthType.APPLE), any(), any())).thenReturn(true);
        adapter = newAdapter(false);
    }

    @Test
    void authorize_iOS_aud_토큰이면_APPLE_PendingOAuthProfile_반환() {
        PendingOAuthProfile result = authorizeWithoutNonce(sign(claims -> claims.audience(IOS_AUD)));

        assertThat(result.getIdentifyId()).isEqualTo(APPLE_SUB);
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getOauthType()).isEqualTo(OauthType.APPLE);
        assertThat(result.getUserId()).isNull();
    }

    @Test
    void authorize_검증된_aud를_oauthClientId로_반환한다() {
        // code 교환의 client_id 는 code 를 발급받은 클라이언트여야 한다.
        assertThat(authorizeWithoutNonce(sign(claims -> claims.audience(IOS_AUD))).getOauthClientId())
                .isEqualTo(IOS_AUD);
        assertThat(authorizeWithoutNonce(sign(claims -> claims.audience(ANDROID_AUD))).getOauthClientId())
                .isEqualTo(ANDROID_AUD);
    }

    @Test
    void authorize_aud가_설정된_허용목록_밖이면_UNAUTHENTICATED() {
        assertRejected(sign(claims -> claims.audience("com.other.app")));
    }

    @Test
    void authorize_email_verified와_is_private_email이_문자열이든_boolean이든_통과() {
        String stringClaims = sign(claims -> claims
                .claim("email_verified", "true").claim("is_private_email", "true"));
        String booleanClaims = sign(claims -> claims
                .claim("email_verified", true).claim("is_private_email", false));

        assertThat(authorizeWithoutNonce(stringClaims).getIdentifyId()).isEqualTo(APPLE_SUB);
        assertThat(authorizeWithoutNonce(booleanClaims).getIdentifyId()).isEqualTo(APPLE_SUB);
    }

    @Test
    void authorize_구_앱처럼_nonce_클레임도_raw_nonce도_없으면_통과() {
        // 구 앱 호환을 위한 단계적 배포 동작이다. nonce-required 를 켜면 거절된다.
        assertThat(authorizeWithoutNonce(sign(claims -> {})).getIdentifyId()).isEqualTo(APPLE_SUB);
        verify(nonceReplayStore, never()).markUsed(any(), any(), any());
    }

    @Test
    void authorize_nonce_클레임이_있는데_raw_nonce가_없으면_UNAUTHENTICATED() {
        // 탈취한 새 앱 토큰에서 raw_nonce 만 빼고 보내 검증을 건너뛰는 다운그레이드.
        assertRejected(new OAuthCredential(sign(claims -> claims.claim("nonce", NONCE_CLAIM)), null, null));
    }

    @Test
    void authorize_nonce_필수설정이면_raw_nonce가_없을때_UNAUTHENTICATED() {
        adapter = newAdapter(true);

        assertRejected(new OAuthCredential(sign(claims -> {}), null, null));
    }

    @Test
    void authorize_raw_nonce의_sha256_hex가_nonce_클레임과_같으면_통과() {
        String idToken = sign(claims -> claims.claim("nonce", NONCE_CLAIM));

        PendingOAuthProfile result = adapter.authorize(new OAuthCredential(idToken, RAW_NONCE, null));

        assertThat(result.getIdentifyId()).isEqualTo(APPLE_SUB);
    }

    @Test
    void authorize_검증된_nonce를_토큰_만료까지_1회용으로_소비한다() {
        String idToken = sign(claims -> claims.claim("nonce", NONCE_CLAIM));

        adapter.authorize(new OAuthCredential(idToken, RAW_NONCE, null));

        // exp(10분) + clock skew(60초) 까지는 verifier 가 받아 주므로 그때까지 기억한다.
        verify(nonceReplayStore).markUsed(OauthType.APPLE, NONCE_CLAIM, Duration.ofSeconds(660));
    }

    @Test
    void authorize_이미_쓴_nonce면_UNAUTHENTICATED() {
        when(nonceReplayStore.markUsed(OauthType.APPLE, NONCE_CLAIM, Duration.ofSeconds(660))).thenReturn(false);
        String idToken = sign(claims -> claims.claim("nonce", NONCE_CLAIM));

        assertRejected(new OAuthCredential(idToken, RAW_NONCE, null));
    }

    @Test
    void authorize_다른_이유로_거절될_토큰은_nonce를_소비하지_않는다() {
        String idToken = sign(claims -> claims.claim("nonce", NONCE_CLAIM).claim("email", null));

        assertRejected(new OAuthCredential(idToken, RAW_NONCE, null));
        verify(nonceReplayStore, never()).markUsed(any(), any(), any());
    }

    @Test
    void authorize_nonce가_불일치하면_UNAUTHENTICATED() {
        String idToken = sign(claims -> claims.claim("nonce", sha256LowerHex("other-nonce")));

        assertRejected(new OAuthCredential(idToken, RAW_NONCE, null));
        verify(nonceReplayStore, never()).markUsed(any(), any(), any());
    }

    @Test
    void authorize_raw_nonce가_있는데_nonce_클레임이_없으면_UNAUTHENTICATED() {
        assertRejected(new OAuthCredential(sign(claims -> {}), RAW_NONCE, null));
    }

    @Test
    void authorize_nonce_클레임에_raw_nonce_원문이_들어있으면_UNAUTHENTICATED() {
        // 앱이 해시하지 않고 원문을 Apple 에 넘긴 경우. 원문 그대로 비교해 통과시키면 안 된다.
        String idToken = sign(claims -> claims.claim("nonce", RAW_NONCE));

        assertRejected(new OAuthCredential(idToken, RAW_NONCE, null));
    }

    @Test
    void authorize_nonce_클레임이_대문자_hex면_UNAUTHENTICATED() {
        String idToken = sign(claims -> claims.claim("nonce", NONCE_CLAIM.toUpperCase()));

        assertRejected(new OAuthCredential(idToken, RAW_NONCE, null));
    }

    @Test
    void authorize_서명이_틀리면_UNAUTHENTICATED() {
        String forged = AppleIdTokenFixture.sign(generateKey("apple-kid-1"), NOW, claims -> {});

        assertRejected(forged);
    }

    @Test
    void authorize_JWKS_조회에_실패하면_재시도를_뜻하는_UNAVAILABLE() {
        when(restOperations.exchange(any(RequestEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));
        String idToken = sign(claims -> {});

        // 토큰이 틀린 게 아니므로 재로그인(UNAUTHENTICATED)으로 안내하지 않는다.
        assertThatThrownBy(() -> authorizeWithoutNonce(idToken))
                .isInstanceOfSatisfying(OauthProviderUnavailableException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(Status.UNAVAILABLE);
                    assertThat(e.toDescription()).isEqualTo("OAuth provider is unavailable");
                });
    }

    @Test
    void authorize_sub가_없으면_UNAUTHENTICATED() {
        assertRejected(sign(claims -> claims.subject(null)));
    }

    @Test
    void authorize_email이_없으면_UNAUTHENTICATED() {
        assertRejected(sign(claims -> claims.claim("email", null)));
    }

    @Test
    void 생성자_aud_허용목록이_비어있으면_기동_실패() {
        assertThatThrownBy(() -> new AppleOAuthAuthorizationAdapter(
                new AppleIdTokenVerifier(restOperations, Clock.systemUTC()), nonceReplayStore, List.of(" "), false))
                .isInstanceOf(IllegalStateException.class);
    }

    private AppleOAuthAuthorizationAdapter newAdapter(boolean nonceRequired) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        // 운영 생성자는 실제 Apple JWKS 로 요청하므로 mock RestOperations 를 쓰는 verifier 를 주입한다.
        return new AppleOAuthAuthorizationAdapter(new AppleIdTokenVerifier(restOperations, clock), nonceReplayStore,
                List.of(IOS_AUD, " " + ANDROID_AUD + " "), nonceRequired, clock);
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

    private static String sign(Consumer<JWTClaimsSet.Builder> customizer) {
        return AppleIdTokenFixture.sign(appleKey, NOW, customizer);
    }
}
