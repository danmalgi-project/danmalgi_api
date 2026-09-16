package com.danmalgi.backend.global.grpc.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.global.security.JwtTokenProvider.JwtPayload;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.service.UserService;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;

@ExtendWith(MockitoExtension.class)
class GrpcAuthInterceptorJwtTokenPropagationTest {

    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

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

    @Test
    void 검증된_요청은_원본_JWT를_GrpcContext에_저장해_다운스트림으로_전파한다() {
        // given
        String rawToken = "raw-client-jwt";
        @SuppressWarnings("unchecked")
        MethodDescriptor<Object, Object> methodDescriptor = mock(MethodDescriptor.class);
        when(methodDescriptor.getFullMethodName()).thenReturn("com.danmalgi.dm.v1.DirectMessageService/GetChannels");
        when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);

        when(jwtTokenProvider.validateAndGetPayload(rawToken))
                .thenReturn(new JwtPayload(100L, "device-1"));
        when(userService.getUser(100L)).thenReturn(mock(User.class));

        // 인증 컨텍스트가 활성화된 시점(startCall)에 GrpcContext.JWT_TOKEN 값을 포착한다.
        AtomicReference<String> tokenSeenDownstream = new AtomicReference<>();
        when(serverCallHandler.startCall(eq(serverCall), any(Metadata.class)))
                .thenAnswer(invocation -> {
                    tokenSeenDownstream.set(GrpcContext.JWT_TOKEN.get());
                    return listener;
                });

        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION_HEADER, "Bearer " + rawToken);

        // when
        interceptor.interceptCall(serverCall, metadata, serverCallHandler);

        // then
        assertThat(tokenSeenDownstream.get()).isEqualTo(rawToken);
    }
}
