package com.danmalgi.backend.device.domain;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Device {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String deviceId;
    private String fcmToken;
}
