package com.danmalgi.backend.global.grpc.interceptor;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class GrpcLoggingInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler
    ) {
        String method = serverCall.getMethodDescriptor().getFullMethodName();
        long startTime = System.currentTimeMillis();

        log.info("[gRPC] Request  | method={}", method);

        ServerCall<ReqT, RespT> loggingCall = new ForwardingServerCall.SimpleForwardingServerCall<>(serverCall) {
            @Override
            public void close(Status status, Metadata trailers) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (status.isOk()) {
                    log.info("[gRPC] Response | method={} status=OK elapsed={}ms", method, elapsed);
                } else {
                    log.warn("[gRPC] Response | method={} status={} description={} elapsed={}ms",
                            method, status.getCode(), status.getDescription(), elapsed);
                }
                super.close(status, trailers);
            }
        };

        return serverCallHandler.startCall(loggingCall, metadata);
    }
}
