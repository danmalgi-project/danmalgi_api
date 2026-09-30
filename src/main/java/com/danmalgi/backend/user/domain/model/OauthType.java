package com.danmalgi.backend.user.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * {@code number} 는 proto {@code user.v1.OauthType} 의 wire 값이자 {@code users.oauth_type}
 * 저장값이다. 세 값이 같다는 전제로 {@code UserProto.OauthType.forNumber} 와 chat 서버의
 * int 캐스팅이 동작하므로 proto 와 함께만 바꾼다.
 */
@RequiredArgsConstructor
@Getter
public enum OauthType {
    GOOGLE(0),
    APPLE(1);

    private final int number;
}
