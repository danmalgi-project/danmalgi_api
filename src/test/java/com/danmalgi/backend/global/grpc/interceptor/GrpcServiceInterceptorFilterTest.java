package com.danmalgi.backend.global.grpc.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.external.auth.v1.AuthServiceGrpc;
import com.danmalgi.backend.external.dm.v1.DirectMessageServiceGrpc;
import com.danmalgi.backend.external.user.v1.UserServiceGrpc;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreServiceGrpc;
import com.danmalgi.backend.internal.device_store.v1.DeviceStoreServiceGrpc;
import com.danmalgi.backend.internal.dm_store.v1.DmStoreServiceGrpc;
import com.danmalgi.backend.internal.user_store.v1.UserStoreServiceGrpc;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.user.service.UserService;

import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;

class GrpcServiceInterceptorFilterTest {

    private static final String REFLECTION_SERVICE = "grpc.reflection.v1.ServerReflection";

    private GrpcServiceInterceptorFilter filter;
    private GrpcAuthInterceptor authInterceptor;
    private GrpcInternalAuthInterceptor internalAuthInterceptor;
    private ServerInterceptor otherInterceptor;

    @BeforeEach
    void setUp() {
        filter = new GrpcServiceInterceptorFilter();
        authInterceptor = new GrpcAuthInterceptor(
                mock(JwtTokenProvider.class), mock(UserService.class), mock(PendingAuthStore.class));
        internalAuthInterceptor = new GrpcInternalAuthInterceptor();
        otherInterceptor = new GrpcLoggingInterceptor();
    }

    private static ServerServiceDefinition service(String serviceName) {
        return ServerServiceDefinition.builder(serviceName).build();
    }

    @Test
    void 인증_인터셉터는_external_서비스에_적용된다() {
        assertThat(filter.filter(authInterceptor, service(UserServiceGrpc.SERVICE_NAME))).isTrue();
        assertThat(filter.filter(authInterceptor, service(AuthServiceGrpc.SERVICE_NAME))).isTrue();
        assertThat(filter.filter(authInterceptor, service(DirectMessageServiceGrpc.SERVICE_NAME)))
                .isTrue();
    }

    @Test
    void 인증_인터셉터는_internal_서비스에_적용되지_않는다() {
        assertThat(filter.filter(authInterceptor, service(UserStoreServiceGrpc.SERVICE_NAME)))
                .isFalse();
        assertThat(filter.filter(authInterceptor, service(DmStoreServiceGrpc.SERVICE_NAME)))
                .isFalse();
        assertThat(filter.filter(authInterceptor, service(DeviceStoreServiceGrpc.SERVICE_NAME)))
                .isFalse();
    }

    @Test
    void internal_인터셉터는_internal_서비스에만_적용된다() {
        assertThat(filter.filter(internalAuthInterceptor, service(UserStoreServiceGrpc.SERVICE_NAME)))
                .isTrue();
        assertThat(filter.filter(internalAuthInterceptor, service(DmStoreServiceGrpc.SERVICE_NAME)))
                .isTrue();
        assertThat(filter.filter(internalAuthInterceptor, service(DeviceStoreServiceGrpc.SERVICE_NAME)))
                .isTrue();
    }

    @Test
    void internal_인터셉터는_external_서비스에_적용되지_않는다() {
        assertThat(filter.filter(internalAuthInterceptor, service(UserServiceGrpc.SERVICE_NAME)))
                .isFalse();
        assertThat(filter.filter(internalAuthInterceptor, service(AuthServiceGrpc.SERVICE_NAME)))
                .isFalse();
    }

    @Test
    void 그밖의_인터셉터는_모든_서비스에_적용된다() {
        assertThat(filter.filter(otherInterceptor, service(UserServiceGrpc.SERVICE_NAME))).isTrue();
        assertThat(filter.filter(otherInterceptor, service(UserStoreServiceGrpc.SERVICE_NAME)))
                .isTrue();
        assertThat(filter.filter(otherInterceptor, service(REFLECTION_SERVICE))).isTrue();
    }

    @Test
    void reflection_서비스는_external_로_취급되어_인증_인터셉터가_붙는다() {
        // GrpcAuthInterceptor 의 REFLECTION_METHOD 바이패스가 계속 동작해야 하므로
        // 인터셉터 자체는 붙어 있어야 한다.
        assertThat(filter.filter(authInterceptor, service(REFLECTION_SERVICE))).isTrue();
        assertThat(filter.filter(internalAuthInterceptor, service(REFLECTION_SERVICE))).isFalse();
    }

    @Test
    void chat_store_는_backend_가_클라이언트이므로_internal_allowlist_에_없다() {
        // proto 는 internal/ 아래에 있지만 ImplBase 구현체가 없어 서버로 등록되지 않는다.
        assertThat(filter.filter(authInterceptor, service(ChatStoreServiceGrpc.SERVICE_NAME)))
                .isTrue();
    }
}
