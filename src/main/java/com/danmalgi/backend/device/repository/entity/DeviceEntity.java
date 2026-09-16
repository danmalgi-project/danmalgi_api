package com.danmalgi.backend.device.repository.entity;

import com.danmalgi.backend.user.repository.entity.UserEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Entity
@Table(name = "devices")
@Getter
public class DeviceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column
    private String deviceId;

    @Column
    private String fcmToken;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    public static DeviceEntity of(UserEntity user, String deviceId, String fcmToken) {
        DeviceEntity deviceEntity = new DeviceEntity();
        deviceEntity.user = user;
        deviceEntity.deviceId = deviceId;
        deviceEntity.fcmToken = fcmToken;
        return deviceEntity;
    }

    public void updateUserIdAndFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
