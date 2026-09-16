package com.danmalgi.backend.auth.domain.exception;

import com.danmalgi.backend.global.exceptionhandler.GrpcException;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;

/**
 * pending 세션이 없는 상태에서 Register 가 호출됐다 (TTL 만료 또는 미존재 userId).
 */
@RequiredArgsConstructor
public class PendingRegistrationNotFoundException extends RuntimeException implements GrpcException {
    private final String description;

    @Override
    public Status getStatus() {
        // 유일한 복구 경로가 Authorization 재호출이다. 모바일 클라이언트는 통상
        // UNAUTHENTICATED 에 대해 재로그인 플로우를 자동으로 태우므로 사용자가
        // 회원가입 화면으로 다시 들어온다.
        return Status.UNAUTHENTICATED;
    }

    @Override
    public String toDescription() {
        return description;
    }
}
