package com.danmalgi.backend.auth.grpc.mapper;

import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.external.auth.v1.AuthProto;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.external.user.v1.UserProto;

public class AuthGrpcMapper {
    private AuthGrpcMapper() {}

    /**
     * 닉네임/태그 미확정 프로필을 proto 로 변환한다.
     *
     * <p>{@code name}/{@code tag} 를 세팅하지 않아 proto3 기본값인 빈 문자열로 나간다.
     * {@code status} 는 항상 {@code USER_PENDING} 이며 클라이언트가 이 값으로 신규 가입을 판별한다.
     */
    public static UserProto.User toProtoPendingUser(PendingOAuthProfile pendingProfile) {
        if (pendingProfile == null) {
            throw new IllegalArgumentException("pendingProfile must not be null");
        }

        UserProto.OauthType oauthType = UserProto.OauthType.forNumber(pendingProfile.getOauthType());
        if (oauthType == null) {
            throw new IllegalStateException("Invalid oauthType value: " + pendingProfile.getOauthType());
        }

        UserProto.User.Builder userBuilder = UserProto.User.newBuilder()
                .setOauthType(oauthType)
                .setStatus(UserProto.UserStatus.USER_PENDING);

        if (pendingProfile.getUserId() != null) {
            userBuilder.setId(pendingProfile.getUserId());
        }

        if (pendingProfile.getEmail() != null) {
            userBuilder.setEmail(pendingProfile.getEmail());
        }

        // R2 key 가 아니라 OAuth 제공자의 URL 이므로 변환 없이 통과시킨다.
        if (pendingProfile.getProfileImageUrl() != null) {
            userBuilder.setProfileImageUrl(pendingProfile.getProfileImageUrl());
        }

        return userBuilder.build();
    }

    /**
     * proto3 string 은 미전송과 빈 문자열을 구분하지 못한다. 하위 레이어가 "없음" 을
     * 한 가지로만 다루도록 blank 인 raw_nonce / authorization_code 는 {@code null} 로 바꾼다.
     */
    public static OAuthCredential toDomainOAuthCredential(AuthProto.AuthorizationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        return new OAuthCredential(
                request.getIdToken(),
                blankToNull(request.getRawNonce()),
                blankToNull(request.getAuthorizationCode())
        );
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }

    public static OauthType toDomainOauthType(UserProto.OauthType oauthType) {
        if (oauthType == null || oauthType == UserProto.OauthType.UNRECOGNIZED) {
            throw new IllegalArgumentException("oauthType is invalid");
        }

        return switch (oauthType) {
            case GOOGLE -> OauthType.GOOGLE;
            case APPLE -> OauthType.APPLE;
            case UNRECOGNIZED -> throw new IllegalArgumentException("oauthType is invalid");
        };
    }
}
