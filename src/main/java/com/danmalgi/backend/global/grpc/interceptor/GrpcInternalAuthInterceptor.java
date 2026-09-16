package com.danmalgi.backend.global.grpc.interceptor;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * {@code src/main/proto/internal/} 아래 서비스 전용 인터셉터.
 *
 * <p>internal 서비스는 chat-server / webrtc-server 가 호출하는 서버 간 계약이라
 * 유저 JWT 가 존재하지 않는다. 따라서 {@link GrpcAuthInterceptor} 대신 이 인터셉터가
 * 붙는다. 어떤 서비스에 어떤 인터셉터가 붙는지는
 * {@link GrpcServiceInterceptorFilter} 가 결정한다.
 *
 * <p><b>현재는 검증 없이 통과시킨다.</b> internal 서비스가 8080 공개 포트에 함께
 * 올라가 있으므로 이 상태로는 무인증으로 호출 가능하다. S2S 자격증명(공유 시크릿
 * 헤더 또는 mTLS peer 확인) 검증이 들어갈 자리를 먼저 만들어 둔 것이며, 실제 검증은
 * 후속 이슈에서 이 메서드 안에 구현한다.
 */
public class GrpcInternalAuthInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler
    ) {
        return serverCallHandler.startCall(serverCall, metadata);
    }
}
