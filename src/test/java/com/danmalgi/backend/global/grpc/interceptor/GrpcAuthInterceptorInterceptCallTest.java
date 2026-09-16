package com.danmalgi.backend.global.grpc.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.external.auth.v1.AuthServiceGrpc;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.user.service.UserService;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;

@ExtendWith(MockitoExtension.class)
class GrpcAuthInterceptorInterceptCallTest {

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
    void Authorization_메서드는_인증_없이_통과한다() {
        // given
        String fullMethodName = AuthServiceGrpc.SERVICE_NAME + "/Authorization";
        @SuppressWarnings("unchecked")
        MethodDescriptor<Object, Object> methodDescriptor = mock(MethodDescriptor.class);
        when(methodDescriptor.getFullMethodName()).thenReturn(fullMethodName);
        when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(serverCallHandler.startCall(eq(serverCall), any(Metadata.class))).thenReturn(listener);

        Metadata metadata = new Metadata();

        // when
        interceptor.interceptCall(serverCall, metadata, serverCallHandler);

        // then
        verify(serverCallHandler).startCall(eq(serverCall), eq(metadata));
        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(userService);
        // pending 분기가 무인증 경로 앞으로 끼어들면 안 된다.
        verifyNoInteractions(pendingAuthStore);
    }
}
