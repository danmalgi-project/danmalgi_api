package com.danmalgi.backend.user.domain.model;

/**
 * OAuth 제공자.
 *
 * <p>DB({@code user_oauth_identities.provider})와 pending 세션 키에는 <b>이름</b>으로 저장된다.
 * 상수 이름을 바꾸면 기존 행과 세션이 깨진다. proto wire 값과는 일부러 분리했다 —
 * 번호를 저장하던 시절 proto enum 재번호(GOOGLE 1→0)가 곧 데이터 마이그레이션이 됐다.
 * proto 와의 변환은 {@code AuthGrpcMapper.toDomainOauthType} 의 switch 가 맡는다.
 */
public enum OauthType {
    GOOGLE,
    APPLE
}
