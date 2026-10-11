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
 * {@code users} 행이 아직 없는 pending 세션(pendingSessionId 클레임)의 인터셉터 통과/거부 계약.
 *
 * <p>ACTIVE 유저의 정상 통과는 {@code GrpcAuthInterceptorJwtTokenPropagationTest} 가,
 * 무인증 통과 경로는 {@code GrpcAuthInterceptorInterceptCallTest} 가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
class GrpcAuthInterceptorPendingSessionTest {

    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String TOKEN = "pending-jwt";
    private static final String SESSION_ID = "11111111-1111-1111-1111-111111111111";

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
    private void stubPendingToken() {
        when(jwtTokenProvider.validateAndGetPayload(TOKEN))
                .thenReturn(new JwtPayload(null, SESSION_ID, "device-1"));
    }

    private void stubPendingSessionExists() {
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.of(new PendingOAuthProfile(
                "test@gmail.com", "google-sub-123", OauthType.GOOGLE, null, null, null)));
    }

    @Test
    void pending세션은_Register를_통과하고_PENDING_SESSION_ID는_주입되지만_USER_ID와_JWT_TOKEN은_없다() {
        // given
        stubMethod(AuthServiceGrpc.SERVICE_NAME + "/Register");
        stubPendingToken();
        stubPendingSessionExists();

        AtomicReference<Long> userIdSeen = new AtomicReference<>();
        AtomicReference<String> pendingSessionIdSeen = new AtomicReference<>();
        AtomicReference<String> deviceIdSeen = new AtomicReference<>();
        AtomicReference<User> userSeen = new AtomicReference<>();
        AtomicReference<String> tokenSeen = new AtomicReference<>();
        when(serverCallHandler.startCall(eq(serverCall), any(Metadata.class))).thenAnswer(invocation -> {
            userIdSeen.set(GrpcContext.USER_ID.get());
            pendingSessionIdSeen.set(GrpcContext.PENDING_SESSION_ID.get());
            deviceIdSeen.set(GrpcContext.DEVICE_ID.get());
            userSeen.set(GrpcContext.USER.get());
            tokenSeen.set(GrpcContext.JWT_TOKEN.get());
            return listener;
        });

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        assertThat(pendingSessionIdSeen.get()).isEqualTo(SESSION_ID);
        assertThat(deviceIdSeen.get()).isEqualTo("device-1");
        // pending 토큰이 chat-server 등 다운스트림으로 새면 안 된다.
        assertThat(userIdSeen.get()).isNull();
        assertThat(tokenSeen.get()).isNull();
        assertThat(userSeen.get()).isNull();
        verify(serverCall, never()).close(any(), any());
    }

    @Test
    void pending세션은_VerifyNameAndTag를_통과한다() {
        // given
        stubMethod(UserServiceGrpc.SERVICE_NAME + "/VerifyNameAndTag");
        stubPendingToken();
        stubPendingSessionExists();
        when(serverCallHandler.startCall(eq(serverCall), any(Metadata.class))).thenReturn(listener);

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        verify(serverCallHandler).startCall(eq(serverCall), any(Metadata.class));
        verify(serverCall, never()).close(any(), any());
    }

    @Test
    void 완료마커만_있어도_Register를_통과한다() {
        // given — 응답 유실 후 재시도. pending 세션은 지워졌고 완료 마커만 남아 있다.
        stubMethod(AuthServiceGrpc.SERVICE_NAME + "/Register");
        stubPendingToken();
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.empty());
        when(pendingAuthStore.findCompletedUserId(SESSION_ID)).thenReturn(Optional.of(100L));
        when(serverCallHandler.startCall(eq(serverCall), any(Metadata.class))).thenReturn(listener);

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        verify(serverCallHandler).startCall(eq(serverCall), any(Metadata.class));
        verify(serverCall, never()).close(any(), any());
    }

    @Test
    void pending세션이_GetUserByToken을_호출하면_FAILED_PRECONDITION이고_Redis를_조회하지_않는다() {
        // given
        stubMethod(UserServiceGrpc.SERVICE_NAME + "/GetUserByToken");
        stubPendingToken();

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(status.capture(), any(Metadata.class));
        assertThat(status.getValue().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(status.getValue().getDescription()).isEqualTo("user registration is not completed");
        verify(serverCallHandler, never()).startCall(any(), any());
        verifyNoInteractions(pendingAuthStore);
    }

    @Test
    void pending세션이_UpsertFcmToken을_호출하면_FAILED_PRECONDITION() {
        // given
        // devices.user_id FK 때문에 유저 행 없이는 저장할 수 없다.
        stubMethod(AuthServiceGrpc.SERVICE_NAME + "/UpsertFcmToken");
        stubPendingToken();

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
    void pending세션도_완료마커도_없으면_UNAUTHENTICATED() {
        // given
        stubMethod(AuthServiceGrpc.SERVICE_NAME + "/Register");
        stubPendingToken();
        when(pendingAuthStore.find(SESSION_ID)).thenReturn(Optional.empty());
        when(pendingAuthStore.findCompletedUserId(SESSION_ID)).thenReturn(Optional.empty());

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(status.capture(), any(Metadata.class));
        assertThat(status.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(status.getValue().getDescription()).isEqualTo("pending registration session not found");
        verify(serverCallHandler, never()).startCall(any(), any());
    }

    @Test
    void userId토큰인데_DB행이_없으면_UNAUTHENTICATED() {
        // given
        // 기존에는 UserNotFoundException 이 인터셉터를 빠져나가 미통제 status 로 나갔다.
        stubMethod(UserServiceGrpc.SERVICE_NAME + "/GetUserByToken");
        when(jwtTokenProvider.validateAndGetPayload(TOKEN)).thenReturn(new JwtPayload(999L, null, "device-1"));
        when(userService.getUser(999L)).thenThrow(new UserNotFoundException("user not found"));

        // when
        interceptor.interceptCall(serverCall, bearerMetadata(), serverCallHandler);

        // then
        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(status.capture(), any(Metadata.class));
        assertThat(status.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(status.getValue().getDescription()).isEqualTo("user not found for token");
        verify(serverCallHandler, never()).startCall(any(), any());
        // userId 토큰 경로는 pending 저장소를 전혀 보지 않는다 — DB 조회만으로 판정이 끝난다.
        verifyNoInteractions(pendingAuthStore);
    }

    @Test
    void ACTIVE_유저는_pending_저장소를_조회하지_않고_USER가_주입된다() {
        // given
        // hot path 다. Redis GET 이 매 요청에 붙으면 안 된다.
        stubMethod(UserServiceGrpc.SERVICE_NAME + "/GetUserByToken");
        User activeUser = mock(User.class);
        when(jwtTokenProvider.validateAndGetPayload(TOKEN)).thenReturn(new JwtPayload(1L, null, "device-1"));
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
