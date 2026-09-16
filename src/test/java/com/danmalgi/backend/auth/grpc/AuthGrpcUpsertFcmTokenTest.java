package com.danmalgi.backend.auth.grpc;

import com.danmalgi.backend.auth.grpc.validator.AuthGrpcValidator;
import com.danmalgi.backend.auth.service.AuthService;
import com.danmalgi.backend.external.auth.v1.AuthProto;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.google.protobuf.Empty;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthGrpcUpsertFcmTokenTest {

    @Mock
    private AuthGrpcValidator authGrpcValidator;

    @Mock
    private AuthService authService;

    @Mock
    private StreamObserver<Empty> responseObserver;

    private AuthGrpc authGrpc;

    @BeforeEach
    void setUp() {
        authGrpc = new AuthGrpc(authGrpcValidator, authService);
    }

    @Test
    void upsertFcmToken_validator_예외발생시_예외_전파() {
        AuthProto.UpsertFcmTokenRequest request = AuthProto.UpsertFcmTokenRequest.newBuilder()
                .setFcmToken("")
                .build();
        doThrow(new IllegalArgumentException("fcmToken must not be blank"))
                .when(authGrpcValidator).validateUpsertFcmTokenRequest(request);

        assertThatThrownBy(() -> authGrpc.upsertFcmToken(request, responseObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fcmToken must not be blank");
    }

    @Test
    void upsertFcmToken_정상_요청이면_onNext와_onCompleted_호출() {
        AuthProto.UpsertFcmTokenRequest request = AuthProto.UpsertFcmTokenRequest.newBuilder()
                .setFcmToken("fcm-token-123")
                .build();

        Context.current()
                .withValue(GrpcContext.USER_ID, 1L)
                .withValue(GrpcContext.DEVICE_ID, "device-1")
                .run(() -> authGrpc.upsertFcmToken(request, responseObserver));

        verify(authService).upsertFcmToken(1L, "device-1", "fcm-token-123");
        verify(responseObserver).onNext(Empty.getDefaultInstance());
        verify(responseObserver).onCompleted();
    }
}
