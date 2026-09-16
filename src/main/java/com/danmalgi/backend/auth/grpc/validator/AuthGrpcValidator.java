package com.danmalgi.backend.auth.grpc.validator;

import com.danmalgi.backend.external.auth.v1.AuthProto;
import com.danmalgi.backend.external.user.v1.UserProto;
import org.springframework.stereotype.Component;

@Component
public class AuthGrpcValidator {
    public void validateAuthorizationRequest(AuthProto.AuthorizationRequest request) {
        if (request.getIdToken().isBlank()) {
            throw new IllegalArgumentException("idToken must not be blank");
        }

        if (request.getDeviceId().isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }

        if (request.getOauthType() == UserProto.OauthType.UNRECOGNIZED) {
            throw new IllegalArgumentException("oauthType is invalid");
        }
    }

    public void validateRegisterRequest(AuthProto.RegisterRequest request) {
        if (request.getNickname().isBlank()) {
            throw new IllegalArgumentException("nickname must not be blank");
        }

        if (request.getTag().isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
    }

    public void validateUpsertFcmTokenRequest(AuthProto.UpsertFcmTokenRequest request) {
        if (request.getFcmToken().isBlank()) {
            throw new IllegalArgumentException("fcmToken must not be blank");
        }
    }
}
