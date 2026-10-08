package com.danmalgi.backend.auth.domain.exception;

import com.danmalgi.backend.global.exceptionhandler.GrpcException;
import io.grpc.Status;
import lombok.Getter;

/**
 * authorization code 를 refresh token 으로 바꾸지 못했다.
 *
 * <p>클라이언트가 할 일이 원인마다 달라서 status 를 나눈다. 메시지(로그용)는
 * 클라이언트에 내보내지 않고 description 은 원인별 고정 문구만 쓴다.
 */
@Getter
public class AuthorizationCodeExchangeException extends RuntimeException implements GrpcException {
    private final Reason reason;

    public AuthorizationCodeExchangeException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AuthorizationCodeExchangeException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    @Override
    public Status getStatus() {
        return reason.status;
    }

    @Override
    public String toDescription() {
        return reason.description;
    }

    public enum Reason {
        // code 누락·만료·재사용. 다시 로그인하면 새 code 를 받는다.
        REJECTED(Status.UNAUTHENTICATED, "Invalid authorization code"),
        // code 가 idToken 과 다른 Apple 계정의 것이다. 다른 계정의 토큰을 섞어 저장하려는 시도로 보고
        // 기존 유저여도 로그인을 막는다. 클라이언트에는 REJECTED 와 같은 문구만 보인다.
        SUBJECT_MISMATCH(Status.UNAUTHENTICATED, "Invalid authorization code"),
        // 네트워크 오류나 제공자 5xx. 같은 요청을 잠시 후 재시도할 수 있다.
        UNAVAILABLE(Status.UNAVAILABLE, "Authorization server is unavailable"),
        // invalid_client 등 서버 설정 오류. 클라이언트가 고칠 수 없다.
        FAILED(Status.INTERNAL, "Authorization failed");

        private final Status status;
        private final String description;

        Reason(Status status, String description) {
            this.status = status;
            this.description = description;
        }
    }
}
