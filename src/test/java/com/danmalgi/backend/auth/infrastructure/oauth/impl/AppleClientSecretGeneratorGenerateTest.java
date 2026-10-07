package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleClientSecretGeneratorGenerateTest {

    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    private static final String CLIENT_ID = "com.danmalgi.mobile";

    private static KeyPair keyPair;
    private static String pem;

    @BeforeAll
    static void generateKey() throws Exception {
        // Apple .p8 과 같은 P-256 PKCS#8 키
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    private AppleClientSecretGenerator generator(String privateKeyPem) {
        return new AppleClientSecretGenerator("TEAMID1234", "KEYID56789", privateKeyPem,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void generate_ES256_헤더에_kid를_넣는다() throws Exception {
        SignedJWT jwt = SignedJWT.parse(generator(pem).generate(CLIENT_ID));

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEYID56789");
    }

    @Test
    void generate_iss는_team_id_sub는_client_id_aud는_apple이다() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(generator(pem).generate(CLIENT_ID)).getJWTClaimsSet();

        assertThat(claims.getIssuer()).isEqualTo("TEAMID1234");
        assertThat(claims.getSubject()).isEqualTo(CLIENT_ID);
        assertThat(claims.getAudience()).isEqualTo(List.of("https://appleid.apple.com"));
    }

    @Test
    void generate_iat는_현재시각_exp는_5분뒤() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(generator(pem).generate(CLIENT_ID)).getJWTClaimsSet();

        assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void generate_서명이_공개키로_검증된다() throws Exception {
        SignedJWT jwt = SignedJWT.parse(generator(pem).generate(CLIENT_ID));

        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
    }

    @Test
    void generate_env로_줄바꿈이_문자열_역슬래시n으로_들어와도_읽는다() throws Exception {
        String escaped = pem.replace("\n", "\\n");

        SignedJWT jwt = SignedJWT.parse(generator(escaped).generate(CLIENT_ID));

        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
    }

    @Test
    void 생성자_잘못된_p8이면_기동_실패() {
        assertThatThrownBy(() -> generator("-----BEGIN PRIVATE KEY-----\nnot-a-key\n-----END PRIVATE KEY-----"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("oauth.apple.private-key is not a valid PKCS#8 EC private key");
    }

    @Test
    void 생성자_team_id가_비어있으면_기동_실패() {
        assertThatThrownBy(() -> new AppleClientSecretGenerator(" ", "KEYID56789", pem, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
