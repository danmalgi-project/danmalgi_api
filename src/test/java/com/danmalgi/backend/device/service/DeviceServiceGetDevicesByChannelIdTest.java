package com.danmalgi.backend.device.service;

import com.danmalgi.backend.device.domain.Device;
import com.danmalgi.backend.device.repository.entity.DeviceEntity;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.fixture.UserFixture;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceGetDevicesByChannelIdTest {

    @Mock
    private DeviceJpaRepository deviceJpaRepository;

    @Mock
    private UserDirectMessageChannelJpaRepository udmcJpaRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(deviceJpaRepository, udmcJpaRepository, redisTemplate);
    }

    @Test
    void getDevicesByChannelId_channelId로_조회하면_해당_채널_유저들의_디바이스를_반환한다() {
        UserEntity userEntity = UserFixture.createUserEntity(1L, "Alice", "00001");
        UserDirectMessageChannelEntity udmcEntity = mock(UserDirectMessageChannelEntity.class);
        when(udmcEntity.getUser()).thenReturn(userEntity);

        DeviceEntity deviceEntity = mock(DeviceEntity.class);
        when(deviceEntity.getUser()).thenReturn(userEntity);
        when(deviceEntity.getDeviceId()).thenReturn("device-1");
        when(deviceEntity.getFcmToken()).thenReturn("fcm-token");

        when(udmcJpaRepository.findAllByDirectMessageChannelId(10L)).thenReturn(List.of(udmcEntity));
        when(deviceJpaRepository.findAllByUser_IdIn(anyList())).thenReturn(List.of(deviceEntity));

        List<Device> result = deviceService.getDevicesByChannelId(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeviceId()).isEqualTo("device-1");
    }

    @Test
    void getDevicesByChannelId_채널에_유저가_없으면_빈_리스트를_반환한다() {
        when(udmcJpaRepository.findAllByDirectMessageChannelId(10L)).thenReturn(List.of());
        when(deviceJpaRepository.findAllByUser_IdIn(List.of())).thenReturn(List.of());

        List<Device> result = deviceService.getDevicesByChannelId(10L);

        assertThat(result).isEmpty();
    }
}
