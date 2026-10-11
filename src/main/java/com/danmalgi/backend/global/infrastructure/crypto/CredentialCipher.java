package com.danmalgi.backend.global.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 외부 제공자 자격증명(refresh token 등)을 DB/Redis 에 넣기 전에 AES-256-GCM 으로 암호화한다.
 *
 * <p>형식은 {@code v1:base64(iv || ciphertext+tag)} 다. 접두어는 키 교체 시 어느 키로
 * 풀어야 하는지 구분하려고 둔다. 지금은 키가 하나뿐이라 {@code v1} 만 받는다.
 */
@Component
public class CredentialCipher {
    static final String VERSION_PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCipher(@Value("${crypto.credential-key}") String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("crypto.credential-key must be base64", e);
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException("crypto.credential-key must be 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        // GCM 은 같은 키에서 IV 가 겹치면 기밀성이 깨지므로 매번 새로 뽑는다.
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("credential encryption failed", e);
        }
    }

    /** 지금은 테스트만 쓴다. Apple revoke(이슈 #3 의 3-4)에서 저장된 토큰을 꺼낼 때 쓴다. */
    public String decrypt(String encrypted) {
        if (encrypted == null || !encrypted.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("unsupported credential format");
        }
        byte[] payload = Base64.getDecoder().decode(encrypted.substring(VERSION_PREFIX.length()));
        if (payload.length <= IV_BYTES) {
            throw new IllegalArgumentException("credential payload is too short");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 변조됐거나 다른 키로 암호화된 값이다. 원문은 메시지에 넣지 않는다.
            throw new IllegalArgumentException("credential decryption failed", e);
        }
    }
}
