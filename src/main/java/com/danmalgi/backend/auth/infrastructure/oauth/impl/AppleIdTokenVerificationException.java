package com.danmalgi.backend.auth.infrastructure.oauth.impl;

/**
 * {@link AppleIdTokenVerifier} 의 실패. 호출부(로그인 / code 교환)마다 내보낼 도메인 예외가 달라서
 * 도메인 예외를 직접 던지지 않고 종류만 알린다. 이 패키지 밖으로 나가지 않는다.
 */
final class AppleIdTokenVerificationException extends RuntimeException {
    private final Kind kind;

    AppleIdTokenVerificationException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }

    enum Kind {
        // 서명·클레임이 틀렸다. 같은 토큰으로는 다시 해도 실패한다.
        INVALID,
        // JWKS 를 받지 못했다. 토큰은 맞을 수 있으므로 재시도할 수 있다.
        UNAVAILABLE
    }
}
