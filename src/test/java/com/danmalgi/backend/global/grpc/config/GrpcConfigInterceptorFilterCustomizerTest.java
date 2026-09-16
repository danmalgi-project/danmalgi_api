package com.danmalgi.backend.global.grpc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerFactoryCustomizer;
import org.springframework.grpc.server.GrpcServerFactory;
import org.springframework.grpc.server.NettyGrpcServerFactory;

import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.external.user.v1.UserServiceGrpc;
import com.danmalgi.backend.global.grpc.interceptor.GrpcAuthInterceptor;
import com.danmalgi.backend.global.grpc.interceptor.GrpcInternalAuthInterceptor;
import com.danmalgi.backend.global.grpc.interceptor.GrpcServiceInterceptorFilter;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.internal.user_store.v1.UserStoreServiceGrpc;
import com.danmalgi.backend.user.service.UserService;

import io.grpc.ServerServiceDefinition;

class GrpcConfigInterceptorFilterCustomizerTest {

    private GrpcConfig grpcConfig;
    private GrpcAuthInterceptor authInterceptor;
    private GrpcInternalAuthInterceptor internalAuthInterceptor;

    @BeforeEach
    void setUp() {
        grpcConfig = new GrpcConfig();
        authInterceptor = new GrpcAuthInterceptor(
                mock(JwtTokenProvider.class), mock(UserService.class), mock(PendingAuthStore.class));
        internalAuthInterceptor = new GrpcInternalAuthInterceptor();
    }

    private static NettyGrpcServerFactory newFactory() {
        return new NettyGrpcServerFactory("0.0.0.0:0", List.of(), null, null, null);
    }

    private static ServerServiceDefinition service(String serviceName) {
        return ServerServiceDefinition.builder(serviceName).build();
    }

    @Test
    void 필터_주입_전에는_모든_인터셉터가_모든_서비스에_적용된다() {
        NettyGrpcServerFactory factory = newFactory();

        assertThat(factory.supports(authInterceptor, service(UserStoreServiceGrpc.SERVICE_NAME)))
                .isTrue();
    }

    @Test
    void 커스터마이저가_필터를_주입하면_internal_서비스에서_인증_인터셉터가_제외된다() {
        // given
        NettyGrpcServerFactory factory = newFactory();
        GrpcServerFactoryCustomizer customizer =
                grpcConfig.interceptorFilterCustomizer(new GrpcServiceInterceptorFilter());

        // when
        customizer.customize(factory);

        // then
        assertThat(factory.supports(authInterceptor, service(UserStoreServiceGrpc.SERVICE_NAME)))
                .isFalse();
        assertThat(factory.supports(authInterceptor, service(UserServiceGrpc.SERVICE_NAME)))
                .isTrue();
    }

    @Test
    void 커스터마이저가_필터를_주입하면_internal_인터셉터가_internal_서비스에만_적용된다() {
        // given
        NettyGrpcServerFactory factory = newFactory();
        GrpcServerFactoryCustomizer customizer =
                grpcConfig.interceptorFilterCustomizer(new GrpcServiceInterceptorFilter());

        // when
        customizer.customize(factory);

        // then
        assertThat(factory.supports(
                internalAuthInterceptor, service(UserStoreServiceGrpc.SERVICE_NAME))).isTrue();
        assertThat(factory.supports(
                internalAuthInterceptor, service(UserServiceGrpc.SERVICE_NAME))).isFalse();
    }

    @Test
    void DefaultGrpcServerFactory_가_아닌_팩토리는_예외없이_무시한다() {
        GrpcServerFactoryCustomizer customizer =
                grpcConfig.interceptorFilterCustomizer(new GrpcServiceInterceptorFilter());

        assertThatCode(() -> customizer.customize(mock(GrpcServerFactory.class)))
                .doesNotThrowAnyException();
    }
}
