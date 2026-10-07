package com.danmalgi.backend.auth.domain.model;

/**
 * OAuth 제공자 검증에 필요한 클라이언트 입력.
 *
 * <p>문자열 인자를 늘어놓으면 위치가 바뀌어도 컴파일이 통과하므로 하나로 묶는다.
 * {@code rawNonce} 와 {@code authorizationCode} 는 Apple 전용이며, 보내지 않은 경우
 * 빈 문자열이 아니라 {@code null} 이다.
 */
public record OAuthCredential(String idToken, String rawNonce, String authorizationCode) {
}
