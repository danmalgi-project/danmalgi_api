package com.danmalgi.backend.auth.infrastructure.oauth;

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.user.domain.model.OauthType;

public interface OAuthPlatformAuthorizationPort {
    OauthType supportedOauthType();

    /**
     * id_token 을 검증해 닉네임/태그 미확정 프로필을 반환한다.
     *
     * <p>{@code name}/{@code tag}/{@code status} 를 임의 생성하지 않는다 (이슈 #37).
     * 반환값의 {@code userId} 는 항상 {@code null} 이며 {@code AuthService} 가 채운다.
     */
    PendingOAuthProfile authorize(String idToken);
}
