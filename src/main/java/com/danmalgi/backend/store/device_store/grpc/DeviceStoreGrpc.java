package com.danmalgi.backend.store.device_store.grpc;

import com.danmalgi.backend.internal.device_store.v1.DeviceStoreProto;
import com.danmalgi.backend.internal.device_store.v1.DeviceStoreServiceGrpc;
import org.springframework.grpc.server.service.GrpcService;

import com.danmalgi.backend.device.service.DeviceService;
import com.danmalgi.backend.store.device_store.grpc.mapper.DeviceStoreGrpcMapper;
import com.danmalgi.backend.store.device_store.grpc.validator.DeviceStoreGrpcValidator;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@GrpcService
@RequiredArgsConstructor
public class DeviceStoreGrpc extends DeviceStoreServiceGrpc.DeviceStoreServiceImplBase {

    private final DeviceService deviceService;
    private final DeviceStoreGrpcValidator validator;

    @Override
    public void getChannelDevices(DeviceStoreProto.GetChannelDevicesRequest request,
                                  StreamObserver<DeviceStoreProto.GetChannelDevicesResponse> responseObserver) {
        validator.validateGetChannelDevicesRequest(request);
        DeviceStoreProto.GetChannelDevicesResponse response = DeviceStoreProto.GetChannelDevicesResponse.newBuilder()
                .addAllDevices(deviceService.getDevicesByChannelId(request.getChannelId()).stream()
                        .map(DeviceStoreGrpcMapper::toProtoDevice)
                        .toList())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getDevices(DeviceStoreProto.GetDevicesRequest request, StreamObserver<DeviceStoreProto.GetDevicesResponse> responseObserver) {
        DeviceStoreProto.GetDevicesResponse response = DeviceStoreProto.GetDevicesResponse.newBuilder()
                .addAllDevices(deviceService.getDevices(request.getDeviceIdsList()).stream()
                        .map(DeviceStoreGrpcMapper::toProtoDevice)
                        .toList())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
