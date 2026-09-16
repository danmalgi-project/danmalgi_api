package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.user.domain.model.OauthType;
import org.springframework.stereotype.Component;

@Component
public class KakaoOAuthAuthorizationAdapter implements OAuthPlatformAuthorizationPort {
    @Override
    public OauthType supportedOauthType() {
        return OauthType.KAKAO;
    }

    @Override
    public PendingOAuthProfile authorize(String idToken) {
        // TODO: Implement Kakao OAuth authorization flow.
        return null;
    }
}
