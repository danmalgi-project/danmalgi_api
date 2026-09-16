package com.danmalgi.backend.store.user_store.grpc;

import com.danmalgi.backend.internal.user_store.v1.UserStoreProto;
import com.danmalgi.backend.internal.user_store.v1.UserStoreServiceGrpc;
import org.springframework.grpc.server.service.GrpcService;

import com.danmalgi.backend.store.user_store.grpc.mapper.UserStoreGrpcMapper;
import com.danmalgi.backend.user.service.UserService;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@GrpcService
@RequiredArgsConstructor
public class UserStoreGrpc extends UserStoreServiceGrpc.UserStoreServiceImplBase {

    private final UserService userService;

    @Override
    public void getUsers(UserStoreProto.GetUsersRequest request, StreamObserver<UserStoreProto.GetUsersResponse> responseObserver) {
        UserStoreProto.GetUsersResponse response = UserStoreProto.GetUsersResponse.newBuilder()
                .addAllUsers(userService.getUsers(request.getUserIdsList()).stream()
                        .map(UserStoreGrpcMapper::toProtoUser)
                        .toList())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
