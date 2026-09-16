package com.danmalgi.backend.auth.service;

import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.device.domain.Device;
import com.danmalgi.backend.device.repository.entity.DeviceEntity;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.device.service.DeviceService;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.danmalgi.backend.user.repository.entity.UserEntity;

@ExtendWith(MockitoExtension.class)
class AuthServiceUpsertFcmTokenTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private DeviceJpaRepository deviceJpaRepository;

    @Mock
    private DeviceService deviceService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private OAuthPlatformAuthorizationPort oauthPort;

    @Mock
    private R2Uploader r2Uploader;

    @Mock
    private PendingAuthStore pendingAuthStore;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(oauthPort.supportedOauthType()).thenReturn(OauthType.GOOGLE);
        when(userJpaRepository.findById(any())).thenReturn(Optional.of(mock(UserEntity.class)));
        authService = new AuthService(
                userJpaRepository,
                deviceJpaRepository,
                deviceService,
                jwtTokenProvider,
                List.of(oauthPort),
                r2Uploader,
                pendingAuthStore
        );
    }

    @Test
    void upsertFcmToken_기존_device_존재하면_fcmToken_업데이트_후_save_호출() {
        DeviceEntity existingDevice = DeviceEntity.of(mock(UserEntity.class), "device-1", "old-fcm-token");
        when(deviceJpaRepository.findFirstByDeviceId("device-1")).thenReturn(Optional.of(existingDevice));

        authService.upsertFcmToken(1L, "device-1", "new-fcm-token");

        verify(deviceJpaRepository).save(existingDevice);
    }

    @Test
    void upsertFcmToken_기존_device_없으면_신규_DeviceEntity_생성_후_save_호출() {
        when(deviceJpaRepository.findFirstByDeviceId("device-1")).thenReturn(Optional.empty());

        authService.upsertFcmToken(1L, "device-1", "fcm-token-123");

        verify(deviceJpaRepository).save(any(DeviceEntity.class));
    }

    @Test
    void upsertFcmToken_정상_처리_후_deviceService_putDevice_호출() {
        when(deviceJpaRepository.findFirstByDeviceId("device-1")).thenReturn(Optional.empty());

        authService.upsertFcmToken(1L, "device-1", "fcm-token-123");

        verify(deviceService).putDevice(eq("device-1"), any(Device.class));
    }

    @Test
    void upsertFcmToken_신규_device_생성_시_userId가_설정됨() {
        UserEntity userEntity = mock(UserEntity.class);
        when(userEntity.getId()).thenReturn(1L);
        when(deviceJpaRepository.findFirstByDeviceId("device-1")).thenReturn(Optional.empty());
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        authService.upsertFcmToken(1L, "device-1", "fcm-token-123");

        ArgumentCaptor<DeviceEntity> captor = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(deviceJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isNotNull();
        assertThat(captor.getValue().getUser().getId()).isEqualTo(1L);
    }
}
