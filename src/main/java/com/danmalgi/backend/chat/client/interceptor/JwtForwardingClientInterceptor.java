package com.danmalgi.backend.chat.client.interceptor;

import com.danmalgi.backend.global.grpc.context.GrpcContext;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * 현재 gRPC 컨텍스트에 보관된 클라이언트 원본 JWT를 아웃바운드 호출의
 * {@code Authorization: Bearer <token>} 헤더로 실어 chat-server 인증을 통과시킨다.
 * 토큰은 {@link com.danmalgi.backend.global.grpc.interceptor.GrpcAuthInterceptor} 가
 * 인바운드 요청에서 검증한 뒤 {@link GrpcContext#JWT_TOKEN} 에 심어 둔다.
 */
public class JwtForwardingClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next
    ) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String token = GrpcContext.JWT_TOKEN.get();
                if (token != null && !token.isBlank()) {
                    headers.put(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
