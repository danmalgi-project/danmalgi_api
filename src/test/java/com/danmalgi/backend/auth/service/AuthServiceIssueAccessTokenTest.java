package com.danmalgi.backend.auth.service;

import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.auth.repository.persistence.UserOAuthCredentialJpaRepository;
import com.danmalgi.backend.auth.repository.persistence.UserOAuthIdentityJpaRepository;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.device.service.DeviceService;
import com.danmalgi.backend.global.infrastructure.crypto.CredentialCipher;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceIssueAccessTokenTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private DeviceJpaRepository deviceJpaRepository;

    @Mock
    private DeviceService deviceService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private R2Uploader r2Uploader;

    @Mock
    private PendingAuthStore pendingAuthStore;

    @Mock
    private CredentialCipher credentialCipher;

    @Mock
    private UserOAuthCredentialJpaRepository userOAuthCredentialJpaRepository;

    @Mock
    private UserOAuthIdentityJpaRepository userOAuthIdentityJpaRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userJpaRepository,
                deviceJpaRepository,
                deviceService,
                jwtTokenProvider,
                List.<OAuthPlatformAuthorizationPort>of(),
                r2Uploader,
                pendingAuthStore,
                List.of(),
                credentialCipher,
                userOAuthCredentialJpaRepository,
                userOAuthIdentityJpaRepository
        );
    }

    @Test
    void issueAccessToken_jwtTokenProvider의_반환값을_그대로_돌려준다() {
        when(jwtTokenProvider.generateToken(100L, "device-1")).thenReturn("jwt-token");

        String result = authService.issueAccessToken(100L, "device-1");

        assertThat(result).isEqualTo("jwt-token");
    }
}
