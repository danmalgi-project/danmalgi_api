package com.danmalgi.backend.auth.fixture;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * Apple id_token 과 같은 모양(alg=RS256, kid, typ 없음)의 테스트 토큰을 만든다.
 * 로그인 어댑터·code 교환·verifier 테스트가 같은 규칙으로 토큰을 만들게 하려고 모았다.
 */
public final class AppleIdTokenFixture {
    public static final String ISSUER = "https://appleid.apple.com";
    public static final String IOS_AUD = "com.danmalgi.mobile";
    public static final String ANDROID_AUD = "com.danmalgi.mobile.service";
    public static final String APPLE_SUB = "001234.0123456789abcdef0123456789abcdef.0123";
    public static final String EMAIL = "abc123@privaterelay.appleid.com";

    private AppleIdTokenFixture() {
    }

    public static RSAKey generateKey(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 기준 시각 {@code now} 에 발급되어 10분 뒤 만료되는 정상 클레임. */
    public static JWTClaimsSet.Builder validClaims(Instant now) {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(IOS_AUD)
                .subject(APPLE_SUB)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .claim("email", EMAIL)
                .claim("email_verified", true)
                .claim("is_private_email", true);
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

    // 운영 코드의 헬퍼를 쓰지 않고 따로 계산해 같은 실수를 공유하지 않게 한다.
    public static String sha256LowerHex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
