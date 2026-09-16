package com.danmalgi.backend.user.grpc;

import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.grpc.mapper.UserGrpcMapper;
import com.danmalgi.backend.user.grpc.validator.UserGrpcValidator;
import com.danmalgi.backend.user.service.UserService;
import com.danmalgi.backend.external.user.v1.UserProto;
import com.danmalgi.backend.external.user.v1.UserServiceGrpc;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class UserGrpc extends UserServiceGrpc.UserServiceImplBase {
    private final UserGrpcValidator userGrpcValidator;
    private final UserService userService;

    @Override
    public void getUserByToken(Empty request, StreamObserver<UserProto.GetUserByTokenResponse> responseObserver) {
        Long userId = GrpcContext.USER_ID.get();

        User user = userService.getUser(userId);

        UserProto.GetUserByTokenResponse response = UserProto.GetUserByTokenResponse.newBuilder()
                .setUser(UserGrpcMapper.toProtoUser(user))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void verifyNameAndTag(UserProto.VerifyNameAndTagRequest request, StreamObserver<Empty> responseObserver) {
        userGrpcValidator.validateVerifyNameAndTagRequest(request);

        userService.verifyNameAndTag(request.getName(), request.getTag());

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void uploadProfile(UserProto.UploadProfileRequest request, StreamObserver<UserProto.UploadProfileResponse> responseObserver) {
        userGrpcValidator.validateUploadProfileRequest(request);

        Long userId = GrpcContext.USER_ID.get();

        User user = userService.uploadProfile(userId, request.getImage().toByteArray(), request.getExtension());

        responseObserver.onNext(UserGrpcMapper.toUploadProfileResponse(user));
        responseObserver.onCompleted();
    }
}
