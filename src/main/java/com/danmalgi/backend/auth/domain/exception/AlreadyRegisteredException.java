package com.danmalgi.backend.auth.domain.exception;

import com.danmalgi.backend.global.exceptionhandler.GrpcException;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;

/**
 * 이미 ACTIVE 인 유저가 (userId 클레임이 있는) 등록 완료 토큰으로 {@code Register} 를 다시 불렀다.
 *
 * <p>pending 토큰이 아니므로 {@code PendingRegistrationNotFoundException} 과는 원인이 다르다 —
 * 세션이 없는 게 아니라 애초에 가입할 대상이 없다. 정상 클라이언트에서는 생기지 않는다.
 */
@RequiredArgsConstructor
public class AlreadyRegisteredException extends RuntimeException implements GrpcException {
    private final String description;

    @Override
    public Status getStatus() {
        return Status.ALREADY_EXISTS;
    }

    @Override
    public String toDescription() {
        return description;
    }
}
