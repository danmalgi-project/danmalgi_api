package com.danmalgi.backend.global.exceptionhandler;

import com.danmalgi.backend.auth.domain.exception.PendingRegistrationNotFoundException;
import io.grpc.Status;
import io.grpc.StatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalGrpcExceptionHandlerHandleExceptionTest {

    private GlobalGrpcExceptionHandler globalGrpcExceptionHandler;

    @BeforeEach
    void setUp() {
        globalGrpcExceptionHandler = new GlobalGrpcExceptionHandler();
    }

    @Test
    void DataIntegrityViolationException은_ALREADY_EXISTS로_매핑된다() {
        // Register 의 신규 노출면 (D6). saveAndFlush 가 uk_users_name_tag 경합으로 터지면
        // 이 예외가 AuthGrpc.register 밖으로 전파된다 — 클라이언트가 재입력을 유도할 수 있는
        // status 여야 한다.
        StatusException result = globalGrpcExceptionHandler.handleException(
                new DataIntegrityViolationException("uk_users_name_tag"));

        assertThat(result).isNotNull();
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(result.getStatus().getDescription()).contains("uk_users_name_tag");
    }

    @Test
    void PendingRegistrationNotFoundException은_UNAUTHENTICATED로_매핑된다() {
        // R3. 유일한 복구 경로가 Authorization 재호출이라, 모바일 클라이언트의 재로그인
        // 플로우를 태울 수 있는 status 여야 한다.
        StatusException result = globalGrpcExceptionHandler.handleException(
                new PendingRegistrationNotFoundException("pending registration not found: id=999"));

        assertThat(result).isNotNull();
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(result.getStatus().getDescription()).contains("id=999");
    }
}
