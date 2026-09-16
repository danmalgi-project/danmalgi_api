package com.danmalgi.backend.auth.grpc;

import com.danmalgi.backend.auth.grpc.dto.AuthorizeResponse;
import com.danmalgi.backend.auth.grpc.mapper.AuthGrpcMapper;
import com.danmalgi.backend.auth.grpc.validator.AuthGrpcValidator;
import com.danmalgi.backend.auth.service.AuthService;
import com.danmalgi.backend.external.auth.v1.AuthProto;
import com.danmalgi.backend.external.auth.v1.AuthServiceGrpc.AuthServiceImplBase;
import com.danmalgi.backend.external.user.v1.UserProto;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.grpc.mapper.UserGrpcMapper;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Optional;

@GrpcService
@RequiredArgsConstructor
public class AuthGrpc extends AuthServiceImplBase {
    private final AuthGrpcValidator authGrpcValidator;
    private final AuthService authService;

    @Override
    public void authorization(AuthProto.AuthorizationRequest request, StreamObserver<AuthProto.AuthorizationResponse> responseObserver) {
        authGrpcValidator.validateAuthorizationRequest(request);

        OauthType oauthType = AuthGrpcMapper.toDomainOauthType(request.getOauthType());

        AuthorizeResponse result = authService.authorize(
                request.getIdToken(),
                request.getDeviceId(),
                oauthType
        );

        UserProto.User protoUser = result.isPending()
                ? AuthGrpcMapper.toProtoPendingUser(result.pendingProfile())
                : UserGrpcMapper.toProtoUser(result.user());

        AuthProto.AuthorizationResponse response = AuthProto.AuthorizationResponse.newBuilder()
                .setUser(protoUser)
                .setAccessToken(Optional.ofNullable(result.jwtToken()).orElse(""))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void register(AuthProto.RegisterRequest request, StreamObserver<AuthProto.RegisterResponse> responseObserver) {
        authGrpcValidator.validateRegisterRequest(request);

        Long userId = GrpcContext.USER_ID.get();
        User user = authService.registerUser(userId, request.getNickname(), request.getTag());

        AuthProto.RegisterResponse response = AuthProto.RegisterResponse.newBuilder()
                .setUser(UserGrpcMapper.toProtoUser(user))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void upsertFcmToken(AuthProto.UpsertFcmTokenRequest request, StreamObserver<Empty> responseObserver) {
        authGrpcValidator.validateUpsertFcmTokenRequest(request);

        Long userId = GrpcContext.USER_ID.get();
        String deviceId = GrpcContext.DEVICE_ID.get();

        authService.upsertFcmToken(userId, deviceId, request.getFcmToken());

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
