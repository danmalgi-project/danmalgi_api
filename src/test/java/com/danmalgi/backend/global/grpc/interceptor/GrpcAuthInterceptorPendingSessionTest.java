package com.danmalgi.backend.global.grpc.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.external.auth.v1.AuthServiceGrpc;
import com.danmalgi.backend.external.user.v1.UserServiceGrpc;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.global.security.JwtTokenProvider.JwtPayload;
import com.danmalgi.backend.user.domain.exception.UserNotFoundException;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.service.UserService;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;

/**
 * {@code users} 행이 아직 없는 pending 세션의 인터셉터 통과/거부 계약.
 *
 * <p>ACTIVE 유저의 정상 통과는 {@code GrpcAuthInterceptorJwtTokenPropagationTest} 가,
 * 무인증 통과 경로는 {@code GrpcAuthInterceptorInterceptCallTest} 가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
class GrpcAuthInterceptorPendingSessionTest {

    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String TOKEN = "pending-jwt";
    private static final Long PENDING_USER_ID = 100L;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserService userService;

    @Mock
    private PendingAuthStore pendingAuthStore;

    @Mock
    private ServerCall<Object, Object> serverCall;

    @Mock
    private ServerCallHandler<Object, Object> serverCallHandler;

    @Mock
    private ServerCall.Listener<Object> listener;

    private GrpcAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new GrpcAuthInterceptor(jwtTokenProvider, userService, pendingAuthStore);
    }

    private Metadata bearerMetadata() {
        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION_HEADER, "Bearer " + TOKEN);
        return metadata;
    }

    private void stubMethod(String fullMethodName) {
        @SuppressWarnings("unchecked")
        MethodDescriptor<Object, Object> methodDescriptor = mock(MethodDescriptor.class);
        when(methodDescriptor.getFullMethodName()).thenReturn(fullMethodName);
        when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);
    }

    /** DB 행은 없고 pending 세션만 있는 상태. */
    private void stubPendingSession() {
        when(jwtTokenProvider.validateAndGetPayload(TOKEN))
                .thenReturn(new JwtPayload(PENDING_USER_ID, "device-1"));
        when(userService.getUser(PENDING_USER_ID))
                .thenThrow(new UserNotFoundException("user not found"));
        when(pendingAuthStore.find(PENDING_USER_ID)).thenReturn(Optional.of(new PendingOAuthProfile(
                PENDING_USER_ID, "test@gmail.com", "google-sub-123", OauthType.GOOGLE.getNumber(), null)));
    }

    @Test
    void pending세션은_Register를_통과하고_USER_ID는_주입되지만_USER는_비어있다() {
        // given
        stubMethod(AuthServiceGrpc.SERVICE_NAME + "/Register");
        stubPendingSession();

        AtomicReference<Long> userIdSeen = new AtomicReference<>();
        AtomicReference<String> deviceIdSeen = new AtomicReference<>();
        AtomicReference<User> userSeen = new AtomicReference<>();
        AtomicReference<String> tokenSeen = new AtomicReference<>();
        when(serverCallHandler.startCall(eq(serverCall), any(Metadata.class))).thenAnswer(invocation -> {
            userIdSeen.set(GrpcContext.USER_ID.get());
            deviceIdSeen.set(GrpcContext.DEVICE_ID.get());
            userSeen.set(GrpcContext.USER.get());
            tokenSeen.set(GrpcContext.JWT_TOKEN.get());
            return listener;
        });

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        assertThat(userIdSeen.get()).isEqualTo(PENDING_USER_ID);
        assertThat(deviceIdSeen.get()).isEqualTo("device-1");
        assertThat(tokenSeen.get()).isEqualTo(TOKEN);
        // users 행이 없어 User 를 조립할 수 없다. 이 Key 를 읽는 코드는 코드베이스에 없다.
        assertThat(userSeen.get()).isNull();
        verify(serverCall, never()).close(any(), any());
    }

    @Test
    void pending세션은_VerifyNameAndTag를_통과한다() {
        // given
        stubMethod(UserServiceGrpc.SERVICE_NAME + "/VerifyNameAndTag");
        stubPendingSession();
        when(serverCallHandler.startCall(eq(serverCall), any(Metadata.class))).thenReturn(listener);

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        verify(serverCallHandler).startCall(eq(serverCall), any(Metadata.class));
        verify(serverCall, never()).close(any(), any());
    }

    @Test
    void pending세션이_GetUserByToken을_호출하면_FAILED_PRECONDITION() {
        // given
        stubMethod(UserServiceGrpc.SERVICE_NAME + "/GetUserByToken");
        stubPendingSession();

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(status.capture(), any(Metadata.class));
        assertThat(status.getValue().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(status.getValue().getDescription()).isEqualTo("user registration is not completed");
        verify(serverCallHandler, never()).startCall(any(), any());
    }

    @Test
    void pending세션이_UpsertFcmToken을_호출하면_FAILED_PRECONDITION() {
        // given
        // devices.user_id FK 때문에 유저 행 없이는 저장할 수 없다.
        // 변경 전에는 성공했던 호출이므로 통제된 status 로 거부해야 한다 (A2).
        stubMethod(AuthServiceGrpc.SERVICE_NAME + "/UpsertFcmToken");
        stubPendingSession();

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(status.capture(), any(Metadata.class));
        assertThat(status.getValue().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(status.getValue().getDescription()).isEqualTo("user registration is not completed");
        verify(serverCallHandler, never()).startCall(any(), any());
    }

    @Test
    void DB행도_pending세션도_없으면_UNAUTHENTICATED() {
        // given
        // 기존에는 UserNotFoundException 이 인터셉터를 빠져나가 미통제 status 로 나갔다.
        stubMethod(UserServiceGrpc.SERVICE_NAME + "/GetUserByToken");
        when(jwtTokenProvider.validateAndGetPayload(TOKEN)).thenReturn(new JwtPayload(999L, "device-1"));
        when(userService.getUser(999L)).thenThrow(new UserNotFoundException("user not found"));
        when(pendingAuthStore.find(999L)).thenReturn(Optional.empty());

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(status.capture(), any(Metadata.class));
        assertThat(status.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(status.getValue().getDescription()).isEqualTo("user not found for token");
        verify(serverCallHandler, never()).startCall(any(), any());
    }

    @Test
    void ACTIVE_유저는_pending_저장소를_조회하지_않고_USER가_주입된다() {
        // given
        // hot path 다. Redis GET 이 매 요청에 붙으면 안 된다.
        stubMethod(UserServiceGrpc.SERVICE_NAME + "/GetUserByToken");
        User activeUser = mock(User.class);
        when(jwtTokenProvider.validateAndGetPayload(TOKEN)).thenReturn(new JwtPayload(1L, "device-1"));
        when(userService.getUser(1L)).thenReturn(activeUser);

        AtomicReference<User> userSeen = new AtomicReference<>();
        when(serverCallHandler.startCall(eq(serverCall), any(Metadata.class))).thenAnswer(invocation -> {
            userSeen.set(GrpcContext.USER.get());
            return listener;
        });

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        assertThat(userSeen.get()).isSameAs(activeUser);
        verifyNoInteractions(pendingAuthStore);
        verify(serverCall, never()).close(any(), any());
    }
}
