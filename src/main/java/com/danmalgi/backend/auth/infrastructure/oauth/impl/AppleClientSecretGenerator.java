package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Apple {@code /auth/token}, {@code /auth/revoke} 에 보낼 client_secret JWT 를 만든다.
 *
 * <p>Apple 은 최대 6개월 유효를 허용하지만 요청마다 5분짜리를 새로 만든다.
 * 유출돼도 피해가 짧고, 만료 갱신·캐시 로직이 필요 없다. 서명 비용은 로그인당 1회라 무시할 만하다.
 */
@Component
public class AppleClientSecretGenerator {
    static final String AUDIENCE = "https://appleid.apple.com";
    static final Duration LIFETIME = Duration.ofMinutes(5);

    private final String teamId;
    private final String keyId;
    private final ECDSASigner signer;
    private final Clock clock;

    @Autowired
    public AppleClientSecretGenerator(
            @Value("${oauth.apple.team-id}") String teamId,
            @Value("${oauth.apple.key-id}") String keyId,
            @Value("${oauth.apple.private-key}") String privateKeyPem
    ) {
        this(teamId, keyId, privateKeyPem, Clock.systemUTC());
    }

    AppleClientSecretGenerator(String teamId, String keyId, String privateKeyPem, Clock clock) {
        if (teamId == null || teamId.isBlank() || keyId == null || keyId.isBlank()) {
            throw new IllegalStateException("oauth.apple.team-id and oauth.apple.key-id must not be blank");
        }
        this.teamId = teamId.trim();
        this.keyId = keyId.trim();
        this.clock = clock;
        // 잘못된 키는 첫 로그인이 아니라 기동 시점에 드러나게 한다.
        this.signer = createSigner(parsePrivateKey(privateKeyPem));
    }

    private static ECDSASigner createSigner(ECPrivateKey privateKey) {
        try {
            return new ECDSASigner(privateKey);
        } catch (JOSEException e) {
            // P-256 이 아닌 EC 키. Apple 키는 항상 P-256 이므로 다른 파일을 넣은 것이다.
            throw new IllegalStateException("oauth.apple.private-key must be a P-256 key", e);
        }
    }

    public String generate(String clientId) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(teamId)
                .subject(clientId)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(LIFETIME)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Apple client_secret signing failed", e);
        }
        return jwt.serialize();
    }

    /**
     * .p8 파일 본문(PKCS#8 PEM)을 읽는다. env 로 넘기면 줄바꿈이 {@code \n} 문자열로 들어오는
     * 경우가 있어 그것도 공백으로 취급한다. BouncyCastle 없이 JDK 만으로 읽으려고 직접 파싱한다.
     */
    static ECPrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("oauth.apple.private-key must not be blank");
        }
        String base64 = pem
                .replace("\\n", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException e) {
            // 키 내용은 메시지에 넣지 않는다.
            throw new IllegalStateException("oauth.apple.private-key is not a valid PKCS#8 EC private key", e);
        }
    }
}
