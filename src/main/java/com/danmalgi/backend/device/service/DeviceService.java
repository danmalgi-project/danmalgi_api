package com.danmalgi.backend.device.service;

import java.util.List;

import org.springframework.cache.annotation.CachePut;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.danmalgi.backend.device.domain.Device;
import com.danmalgi.backend.device.repository.persistence.DeviceJpaRepository;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceService {
private final DeviceJpaRepository deviceJpaRepository;
    private final UserDirectMessageChannelJpaRepository udmcJpaRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @CachePut(value = "device", key = "#p0")
    public Device putDevice(String deviceId, Device device) {
        return device;
    }

    public List<Device> getDevices(List<String> deviceIds) {
        return deviceJpaRepository.findAllByDeviceIdIn(deviceIds).stream()
                .map(e -> new Device(e.getUser().getId(), e.getDeviceId(), e.getFcmToken()))
                .toList();
    }

    public List<Device> getDevicesByChannelId(Long channelId) {
        List<Long> userIds = udmcJpaRepository.findAllByDirectMessageChannelId(channelId)
                .stream()
                .map(e -> e.getUser().getId())
                .toList();

        return deviceJpaRepository.findAllByUser_IdIn(userIds).stream()
                .map(e -> new Device(e.getUser().getId(), e.getDeviceId(), e.getFcmToken()))
                .toList();
    }
}
