package com.danmalgi.backend.auth.grpc.validator;

import com.danmalgi.backend.external.auth.v1.AuthProto;
import com.danmalgi.backend.external.user.v1.UserProto;
import org.springframework.stereotype.Component;

@Component
public class AuthGrpcValidator {
    // 정상 클라이언트는 32~64자 정도를 보낸다. 해시 입력이 무한정 커지는 것만 막는다.
    static final int RAW_NONCE_MAX_LENGTH = 256;
    // Apple/Google idToken 은 1~2KB, Apple code 는 100자 안팎이다. 서명 검증·외부 호출 전에
    // 비정상적으로 큰 입력을 잘라내려는 상한이라 넉넉하게 잡는다.
    static final int ID_TOKEN_MAX_LENGTH = 8192;
    static final int AUTHORIZATION_CODE_MAX_LENGTH = 1024;

    public void validateAuthorizationRequest(AuthProto.AuthorizationRequest request) {
        if (request.getIdToken().isBlank()) {
            throw new IllegalArgumentException("idToken must not be blank");
        }

        if (request.getIdToken().length() > ID_TOKEN_MAX_LENGTH) {
            throw new IllegalArgumentException("idToken must not exceed " + ID_TOKEN_MAX_LENGTH + " characters");
        }

        if (request.getDeviceId().isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }

        if (request.getOauthType() == UserProto.OauthType.UNRECOGNIZED) {
            throw new IllegalArgumentException("oauthType is invalid");
        }

        // APPLE 일 때 필수인지는 여기서 보지 않는다. 구 앱의 Google 요청(wire 1)이 APPLE 로
        // 해석되므로, 여기서 막으면 UNAUTHENTICATED 가 아니라 INVALID_ARGUMENT 로 바뀐다.
        if (request.getRawNonce().length() > RAW_NONCE_MAX_LENGTH) {
            throw new IllegalArgumentException("rawNonce must not exceed " + RAW_NONCE_MAX_LENGTH + " characters");
        }

        if (request.getAuthorizationCode().length() > AUTHORIZATION_CODE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "authorizationCode must not exceed " + AUTHORIZATION_CODE_MAX_LENGTH + " characters");
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
