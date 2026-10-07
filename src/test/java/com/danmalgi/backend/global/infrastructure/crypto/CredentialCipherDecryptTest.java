package com.danmalgi.backend.global.infrastructure.crypto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherDecryptTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final CredentialCipher cipher = new CredentialCipher(KEY);

    @Test
    void decrypt_encrypt의_역이다() {
        assertThat(cipher.decrypt(cipher.encrypt("apple-refresh-token"))).isEqualTo("apple-refresh-token");
    }

    @Test
    void decrypt_변조된_암호문이면_예외() {
        String encrypted = cipher.encrypt("apple-refresh-token");
        byte[] payload = Base64.getDecoder().decode(encrypted.substring("v1:".length()));
        payload[payload.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("credential decryption failed");
    }

    @Test
    void decrypt_다른_키로_암호화된_값이면_예외() {
        byte[] otherKey = new byte[32];
        Arrays.fill(otherKey, (byte) 7);
        String encrypted = new CredentialCipher(Base64.getEncoder().encodeToString(otherKey)).encrypt("token");

        assertThatThrownBy(() -> cipher.decrypt(encrypted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("credential decryption failed");
    }

    @Test
    void decrypt_버전_접두어가_없으면_예외() {
        assertThatThrownBy(() -> cipher.decrypt("plain-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported credential format");
    }
}
