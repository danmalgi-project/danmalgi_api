package com.danmalgi.backend.auth.domain.exception;

import com.danmalgi.backend.global.exceptionhandler.GrpcException;
import io.grpc.Status;

/**
 * OAuth 제공자 쪽 장애로 idToken 을 검증하지 못했다 (JWKS 조회 실패 등).
 *
 * <p>{@link OauthAuthorizeFailException}(UNAUTHENTICATED)과 나누는 이유: 토큰이 틀린 게 아니므로
 * 클라이언트가 재로그인이 아니라 재시도로 대응해야 한다. 메시지(로그용)는 내보내지 않는다.
 */
public class OauthProviderUnavailableException extends RuntimeException implements GrpcException {
    private static final String DESCRIPTION = "OAuth provider is unavailable";

    public OauthProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public Status getStatus() {
        return Status.UNAVAILABLE;
    }

    @Override
    public String toDescription() {
        return DESCRIPTION;
    }
}
