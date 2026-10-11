package com.danmalgi.backend.auth.grpc;

import com.danmalgi.backend.auth.grpc.validator.AuthGrpcValidator;
import com.danmalgi.backend.auth.service.AuthService;
import com.danmalgi.backend.external.auth.v1.AuthProto;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthGrpcRegisterTest {

    private static final String SESSION_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private AuthGrpcValidator authGrpcValidator;

    @Mock
    private AuthService authService;

    @Mock
    private StreamObserver<AuthProto.RegisterResponse> responseObserver;

    private AuthGrpc authGrpc;

    @BeforeEach
    void setUp() {
        authGrpc = new AuthGrpc(authGrpcValidator, authService);
    }

    @Test
    void register_validator_예외발생시_예외_전파() {
        AuthProto.RegisterRequest request = AuthProto.RegisterRequest.newBuilder()
                .setNickname("")
                .setTag("12345")
                .build();
        doThrow(new IllegalArgumentException("nickname must not be blank"))
                .when(authGrpcValidator).validateRegisterRequest(request);

        assertThatThrownBy(() -> authGrpc.register(request, responseObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("nickname must not be blank");
    }

    @Test
    void register_정상_요청이면_registerUser_호출하고_응답한다() {
        AuthProto.RegisterRequest request = AuthProto.RegisterRequest.newBuilder()
                .setNickname("홍길동")
                .setTag("12345")
                .build();
        // AuthService.registerUser 가 이미 public URL 로 변환된 user 를 반환한다고 가정
        User user = new User(1L, "test@test.com", "홍길동", "12345", null, UserStatus.ACTIVE.getNumber());
        user.setProfileImageUrl("https://signed.example.com/profiles/1/img?sig=abc");
        when(authService.registerUser(SESSION_ID, "홍길동", "12345")).thenReturn(user);
        when(authService.issueAccessToken(1L, "device-1")).thenReturn("new.jwt.token");

        Context.current()
                .withValue(GrpcContext.PENDING_SESSION_ID, SESSION_ID)
                .withValue(GrpcContext.DEVICE_ID, "device-1")
                .run(() -> authGrpc.register(request, responseObserver));

        verify(authService).registerUser(SESSION_ID, "홍길동", "12345");
        verify(responseObserver).onNext(argThat(response ->
                response.getUser().getId() == 1L
                        && response.getUser().getProfileImageUrl()
                                .equals("https://signed.example.com/profiles/1/img?sig=abc")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void register_정상_요청이면_재발급된_토큰을_응답한다() {
        AuthProto.RegisterRequest request = AuthProto.RegisterRequest.newBuilder()
                .setNickname("홍길동")
                .setTag("12345")
                .build();
        User user = new User(1L, "test@test.com", "홍길동", "12345", null, UserStatus.ACTIVE.getNumber());
        when(authService.registerUser(SESSION_ID, "홍길동", "12345")).thenReturn(user);
        when(authService.issueAccessToken(1L, "device-1")).thenReturn("new.jwt.token");

        Context.current()
                .withValue(GrpcContext.PENDING_SESSION_ID, SESSION_ID)
                .withValue(GrpcContext.DEVICE_ID, "device-1")
                .run(() -> authGrpc.register(request, responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getAccessToken().equals("new.jwt.token")
        ));
    }
}
