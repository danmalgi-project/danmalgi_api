package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthPlatformAuthorizationPort;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.text.ParseException;

@Component
public class GoogleOAuthAuthorizationAdapter implements OAuthPlatformAuthorizationPort {
    @Override
    public OauthType supportedOauthType() {
        return OauthType.GOOGLE;
    }

    @Override
    public PendingOAuthProfile authorize(String idToken) {
        GoogleOAuthClaims claims = extractGoogleOAuthClaims(idToken);
        return PendingOAuthProfile.builder()
                .email(claims.email())
                .identifyId(claims.sub())
                .oauthType(supportedOauthType().getNumber())
                .build();
    }

    private GoogleOAuthClaims extractGoogleOAuthClaims(String idToken) {
        try {
            JWTClaimsSet claimSet = SignedJWT.parse(idToken).getJWTClaimsSet();

            String sub = claimSet.getSubject();
            String email = claimSet.getStringClaim("email");

            if (sub == null || sub.isBlank()) {
                throw new OauthAuthorizeFailException("idToken does not contain sub claim");
            }
            // email 은 UserEntity.email 이 @NotNull 이라 필수다.
            if (email == null || email.isBlank()) {
                throw new OauthAuthorizeFailException("idToken does not contain email claim");
            }
            // name 클레임은 검사하지 않는다. profile 스코프가 없으면 실리지 않는 값이고,
            // normalizeName 제거로 소비자가 사라졌다 (이슈 #37 요구사항 3).

            return new GoogleOAuthClaims(sub, email);
        } catch (ParseException e) {
            throw new OauthAuthorizeFailException("Invalid Google OAuth idToken format");
        }
    }

    private record GoogleOAuthClaims(String sub, String email) {
    }
}
