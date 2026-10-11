package com.danmalgi.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.global.security.JwtTokenProvider.JwtPayload;

class JwtTokenProviderGeneratePendingTokenTest {

    private static final String SECRET = "01234567890123456789012345678901";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET);

    @Test
    void generatePendingToken_pendingSessionId가_blank면_예외() {
        assertThatThrownBy(() -> jwtTokenProvider.generatePendingToken(" ", "device-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatePendingToken_deviceId가_blank면_예외() {
        assertThatThrownBy(() -> jwtTokenProvider.generatePendingToken("session-1", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatePendingToken_발급된_토큰은_pendingSessionId와_deviceId만_담고_userId클레임은_없다() {
        String token = jwtTokenProvider.generatePendingToken("session-1", "device-1");

        JwtPayload payload = jwtTokenProvider.validateAndGetPayload(token);

        assertThat(payload.pendingSessionId()).isEqualTo("session-1");
        assertThat(payload.deviceId()).isEqualTo("device-1");
        assertThat(payload.userId()).isNull();
    }
}
