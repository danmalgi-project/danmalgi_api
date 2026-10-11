package com.danmalgi.backend.auth.fixture;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

/**
 * Google id_token 과 같은 모양(alg=RS256, kid, typ 없음)의 테스트 토큰을 만든다.
 *
 * <p>{@link AppleIdTokenFixture} 와 코드가 겹치지만 합치지 않는다. 픽스처를 공유하면
 * 한쪽 제공자의 클레임 변경이 다른 쪽 테스트를 끌고 깨진다.
 */
public final class GoogleIdTokenFixture {
    /** Google 은 스킴이 있는 형태와 없는 형태를 모두 발급한다. 둘 다 통과해야 한다. */
    public static final String ISSUER_URL = "https://accounts.google.com";
    public static final String ISSUER_BARE = "accounts.google.com";

    public static final String WEB_AUD = "123456789012-web.apps.googleusercontent.com";
    public static final String IOS_AUD = "123456789012-ios.apps.googleusercontent.com";
    public static final String GOOGLE_SUB = "110123456789012345678";
    public static final String EMAIL = "tester@gmail.com";

    private GoogleIdTokenFixture() {
    }

    public static RSAKey generateKey(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 기준 시각 {@code now} 에 발급되어 1시간 뒤 만료되는 정상 클레임. */
    public static JWTClaimsSet.Builder validClaims(Instant now) {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER_URL)
                .audience(WEB_AUD)
                .subject(GOOGLE_SUB)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("email", EMAIL)
                .claim("email_verified", true)
                .claim("name", "Tester");
    }

    public static String sign(RSAKey key, Instant now, Consumer<JWTClaimsSet.Builder> customizer) {
        JWTClaimsSet.Builder claims = validClaims(now);
        customizer.accept(claims);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims.build());
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }
}
