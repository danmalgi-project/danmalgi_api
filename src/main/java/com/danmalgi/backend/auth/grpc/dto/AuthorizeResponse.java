package com.danmalgi.backend.auth.grpc.dto;

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.user.domain.model.User;

/**
 * Authorization 결과. 두 갈래 중 하나만 채워진다.
 *
 * <p>{@code User} 는 {@code validate()} 가 name/tag 를 강제해 pending 을 표현할 수 없고,
 * 둘을 sealed interface 로 묶으면 {@code user} → {@code auth} 역방향 의존으로
 * 패키지 순환이 생긴다. 그래서 nullable 두 필드 + 정적 팩토리로 둔다.
 */
public record AuthorizeResponse(
    User user,
    PendingOAuthProfile pendingProfile,
    String jwtToken
) {
    public static AuthorizeResponse ofRegisteredUser(User user, String jwtToken) {
        return new AuthorizeResponse(user, null, jwtToken);
    }

    public static AuthorizeResponse ofPendingProfile(PendingOAuthProfile pendingProfile, String jwtToken) {
        return new AuthorizeResponse(null, pendingProfile, jwtToken);
    }

    public boolean isPending() {
        return pendingProfile != null;
    }
}
