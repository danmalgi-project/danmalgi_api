package com.danmalgi.backend.user.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum OauthType {
    NAVER(0),
    GOOGLE(1),
    KAKAO(2);

    private final int number;
}
