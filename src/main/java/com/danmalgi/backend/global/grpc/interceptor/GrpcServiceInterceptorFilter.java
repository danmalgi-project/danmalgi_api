package com.danmalgi.backend.global.grpc.interceptor;

import java.util.Set;

import org.springframework.grpc.server.service.ServerInterceptorFilter;

import com.danmalgi.backend.internal.device_store.v1.DeviceStoreServiceGrpc;
import com.danmalgi.backend.internal.dm_store.v1.DmStoreServiceGrpc;
import com.danmalgi.backend.internal.user_store.v1.UserStoreServiceGrpc;

import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;

/**
 * 글로벌 인터셉터를 서비스 단위로 라우팅한다.
 *
 * <p>external 서비스는 클라이언트가 유저 JWT 를 실어 보내므로
 * {@link GrpcAuthInterceptor} 가 붙고, internal 서비스는 서버 간 호출이라 유저 JWT 가
 * 없으므로 {@link GrpcInternalAuthInterceptor} 가 붙는다. 그 밖의 인터셉터
 * (로깅 등)는 양쪽 모두에 붙는다.
 *
 * <p>allowlist 방식이라 <b>fail-closed</b> 다 — 새로 추가된 서비스는 기본적으로
 * {@link GrpcAuthInterceptor} 로 인증된다. internal 서비스를 추가할 때는
 * {@link #INTERNAL_SERVICES} 에 명시적으로 등록해야 한다.
 *
 * <p>proto 의 {@code package} 에는 {@code internal} 접두어가 없다
 * ({@code user_store.v1} 이며 {@code internal} 은 디렉터리와 {@code java_package}
 * 에만 있다). 따라서 이름 패턴 매칭으로는 판별할 수 없고 allowlist 가 필요하다.
 */
public class GrpcServiceInterceptorFilter implements ServerInterceptorFilter {

    private static final Set<String> INTERNAL_SERVICES = Set.of(
            UserStoreServiceGrpc.SERVICE_NAME,
            DmStoreServiceGrpc.SERVICE_NAME,
            DeviceStoreServiceGrpc.SERVICE_NAME
    );

    @Override
    public boolean filter(ServerInterceptor interceptor, ServerServiceDefinition service) {
        boolean internal = INTERNAL_SERVICES.contains(service.getServiceDescriptor().getName());

        if (interceptor instanceof GrpcAuthInterceptor) {
            return !internal;
        }

        if (interceptor instanceof GrpcInternalAuthInterceptor) {
            return internal;
        }

        return true;
    }
}
