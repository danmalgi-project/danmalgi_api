package com.danmalgi.backend.store.dm_store.grpc;

import org.springframework.grpc.server.service.GrpcService;

import com.danmalgi.backend.store.dm_store.grpc.mapper.DmStoreGrpcMapper;
import com.danmalgi.backend.store.dm_store.grpc.validator.DmStoreGrpcValidator;
import com.danmalgi.backend.internal.dm_store.v1.DmStoreProto.GetChannelNamesRequest;
import com.danmalgi.backend.internal.dm_store.v1.DmStoreProto.GetChannelNamesResponse;
import com.danmalgi.backend.internal.dm_store.v1.DmStoreServiceGrpc.DmStoreServiceImplBase;
import com.danmalgi.backend.udmc.service.UserDirectMessageChannelService;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@GrpcService
@RequiredArgsConstructor
public class DmStoreGrpc extends DmStoreServiceImplBase {
    private final UserDirectMessageChannelService userDirectMessageChannelService;
    private final DmStoreGrpcValidator validator;

    @Override
    public void getChannelNames(GetChannelNamesRequest request,
            StreamObserver<GetChannelNamesResponse> responseObserver) {
        validator.validateGetChannelNamesRequest(request);
        GetChannelNamesResponse response = GetChannelNamesResponse.newBuilder()
                .addAllChannelNames(
                        userDirectMessageChannelService.getChannelNames(request.getChannelId(), request.getUserIdsList())
                                .entrySet().stream()
                                .map(e -> DmStoreGrpcMapper.toProtoChannelName(e.getKey(), e.getValue()))
                                .toList()
                )
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
