package com.danmalgi.backend.store.device_store.grpc.validator;

import com.danmalgi.backend.internal.device_store.v1.DeviceStoreProto;
import org.springframework.stereotype.Component;

@Component
public class DeviceStoreGrpcValidator {
    public void validateGetChannelDevicesRequest(DeviceStoreProto.GetChannelDevicesRequest request) {
        if (request.getChannelId() <= 0) {
            throw new IllegalArgumentException("channelId must be positive");
        }
    }
}
