package com.danmalgi.backend.device.service;

import com.danmalgi.backend.device.domain.Device;
import com.danmalgi.backend.device.repository.entity.DeviceEntity;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.fixture.UserFixture;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceGetDevicesTest {

    @Mock
    private DeviceJpaRepository deviceJpaRepository;

    @Mock
    private UserDirectMessageChannelJpaRepository udmcJpaRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(deviceJpaRepository, udmcJpaRepository, redisTemplate);
    }

    @Test
    void getDevices_deviceId_목록으로_조회하면_해당_디바이스를_반환한다() {
        UserEntity userEntity = UserFixture.createUserEntity(1L, "Alice", "00001");
        DeviceEntity deviceEntity = mock(DeviceEntity.class);
        when(deviceEntity.getUser()).thenReturn(userEntity);
        when(deviceEntity.getDeviceId()).thenReturn("device-1");
        when(deviceEntity.getFcmToken()).thenReturn("fcm-token");

        when(deviceJpaRepository.findAllByDeviceIdIn(List.of("device-1"))).thenReturn(List.of(deviceEntity));

        List<Device> result = deviceService.getDevices(List.of("device-1"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeviceId()).isEqualTo("device-1");
    }

    @Test
    void getDevices_존재하지_않는_deviceId면_빈_리스트를_반환한다() {
        when(deviceJpaRepository.findAllByDeviceIdIn(List.of("not-exist"))).thenReturn(List.of());

        List<Device> result = deviceService.getDevices(List.of("not-exist"));

        assertThat(result).isEmpty();
    }
}
