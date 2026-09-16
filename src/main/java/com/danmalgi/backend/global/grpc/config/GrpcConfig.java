package com.danmalgi.backend.global.grpc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerFactoryCustomizer;
import org.springframework.grpc.server.DefaultGrpcServerFactory;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.service.ServerInterceptorFilter;

import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.global.grpc.interceptor.GrpcAuthInterceptor;
import com.danmalgi.backend.global.grpc.interceptor.GrpcInternalAuthInterceptor;
import com.danmalgi.backend.global.grpc.interceptor.GrpcLoggingInterceptor;
import com.danmalgi.backend.global.grpc.interceptor.GrpcServiceInterceptorFilter;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.user.service.UserService;

import io.grpc.ServerInterceptor;

@Configuration
public class GrpcConfig {
    @Bean
    @GlobalServerInterceptor
    public ServerInterceptor authInterceptor(
        JwtTokenProvider jwtTokenProvider,
        UserService userService,
        PendingAuthStore pendingAuthStore
    ) {
        return new GrpcAuthInterceptor(jwtTokenProvider, userService, pendingAuthStore);
    }

    @Bean
    @GlobalServerInterceptor
    public ServerInterceptor internalAuthInterceptor() {
        return new GrpcInternalAuthInterceptor();
    }

    @Bean
    @GlobalServerInterceptor
    public ServerInterceptor loggingInterceptor() {
        return new GrpcLoggingInterceptor();
    }

    @Bean
    public ServerInterceptorFilter serviceInterceptorFilter() {
        return new GrpcServiceInterceptorFilter();
    }

    /**
     * netty 서버 팩토리에 인터셉터 필터를 주입한다.
     *
     * <p>spring-grpc 오토컨피그는 in-process 팩토리에만
     * {@code setInterceptorFilter} 를 호출하므로, netty 팩토리에는 커스터마이저로
     * 직접 주입해야 한다. 커스터마이저는 서비스 등록 루프보다 먼저 적용되므로
     * (GrpcServerFactoryConfigurations.NettyServerFactoryConfiguration 참조)
     * 이 시점에 주입하면 모든 서비스의 인터셉터 조립에 반영된다.
     */
    @Bean
    public GrpcServerFactoryCustomizer interceptorFilterCustomizer(
        ServerInterceptorFilter serviceInterceptorFilter
    ) {
        return serverFactory -> {
            if (serverFactory instanceof DefaultGrpcServerFactory<?> defaultServerFactory) {
                defaultServerFactory.setInterceptorFilter(serviceInterceptorFilter);
            }
        };
    }
}
