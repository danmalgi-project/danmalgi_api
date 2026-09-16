package com.danmalgi.backend.store.device_store.grpc.mapper;

import com.danmalgi.backend.device.domain.Device;
import com.danmalgi.backend.internal.device_store.v1.DeviceStoreProto;

public class DeviceStoreGrpcMapper {
    private DeviceStoreGrpcMapper() {}

    public static DeviceStoreProto.Device toProtoDevice(Device device) {
        return DeviceStoreProto.Device.newBuilder()
                .setUserId(device.getUserId())
                .setDeviceId(device.getDeviceId())
                .setFcmToken(device.getFcmToken())
                .build();
    }
}
