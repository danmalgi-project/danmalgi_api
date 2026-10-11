package com.danmalgi.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.global.security.JwtTokenProvider.JwtPayload;

class JwtTokenProviderValidateAndGetPayloadTest {

    private static final String SECRET = "01234567890123456789012345678901";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET);

    @Test
    void validateAndGetPayload_userId토큰이면_userId만_채워진다() {
        String token = jwtTokenProvider.generateToken(100L, "device-1");

        JwtPayload payload = jwtTokenProvider.validateAndGetPayload(token);

        assertThat(payload.userId()).isEqualTo(100L);
        assertThat(payload.pendingSessionId()).isNull();
    }

    @Test
    void validateAndGetPayload_pending토큰이면_pendingSessionId만_채워진다() {
        String token = jwtTokenProvider.generatePendingToken("session-1", "device-1");

        JwtPayload payload = jwtTokenProvider.validateAndGetPayload(token);

        assertThat(payload.pendingSessionId()).isEqualTo("session-1");
        assertThat(payload.userId()).isNull();
    }

    @Test
    void validateAndGetPayload_서명이_다른_키면_예외() {
        JwtTokenProvider other = new JwtTokenProvider("98765432109876543210987654321098");
        String token = other.generateToken(100L, "device-1");

        assertThatThrownBy(() -> jwtTokenProvider.validateAndGetPayload(token))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
