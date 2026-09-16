package com.danmalgi.backend.device.repository.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.danmalgi.backend.device.repository.entity.DeviceEntity;

@Repository
public interface DeviceJpaRepository extends JpaRepository<DeviceEntity, Long> {
    Optional<DeviceEntity> findFirstByDeviceId(String deviceId);
    List<DeviceEntity> findAllByDeviceIdIn(List<String> deviceIds);
    List<DeviceEntity> findAllByUser_IdIn(List<Long> userIds);
}
