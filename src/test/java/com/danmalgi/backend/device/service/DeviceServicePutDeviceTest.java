package com.danmalgi.backend.device.service;

import com.danmalgi.backend.device.domain.Device;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DeviceServicePutDeviceTest {

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
    void putDevice_전달된_device를_그대로_반환() {
        Device device = new Device(1L, "device-1", "fcm-token");

        Device result = deviceService.putDevice("device-1", device);

        assertThat(result).isSameAs(device);
    }
}
