package com.danmalgi.backend.global.infrastructure.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherEncryptTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final CredentialCipher cipher = new CredentialCipher(KEY);

    @Test
    void encrypt_v1_접두어가_붙고_평문이_드러나지_않는다() {
        String encrypted = cipher.encrypt("apple-refresh-token");

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).doesNotContain("apple-refresh-token");
    }

    @Test
    void encrypt_같은_평문도_매번_다른_암호문() {
        // IV 를 재사용하면 GCM 기밀성이 깨진다.
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void 생성자_키가_32바이트가_아니면_기동_실패() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new CredentialCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("crypto.credential-key must be 32 bytes (AES-256)");
    }

    @Test
    void 생성자_키가_base64가_아니면_기동_실패() {
        assertThatThrownBy(() -> new CredentialCipher("not base64 !!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("crypto.credential-key must be base64");
    }
}
