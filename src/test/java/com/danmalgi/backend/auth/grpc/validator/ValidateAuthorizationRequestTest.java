package com.danmalgi.backend.auth.grpc.validator;

import com.danmalgi.backend.external.auth.v1.AuthProto;
import com.danmalgi.backend.external.user.v1.UserProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateAuthorizationRequestTest {

    private final AuthGrpcValidator validator = new AuthGrpcValidator();

    @Test
    void validateAuthorizationRequest_idToken이_blank이면_예외발생() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.GOOGLE)
                .build();

        assertThatThrownBy(() -> validator.validateAuthorizationRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idToken must not be blank");
    }

    @Test
    void validateAuthorizationRequest_deviceId가_blank이면_예외발생() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("valid-token")
                .setDeviceId("")
                .setOauthType(UserProto.OauthType.GOOGLE)
                .build();

        assertThatThrownBy(() -> validator.validateAuthorizationRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("deviceId must not be blank");
    }

    @Test
    void validateAuthorizationRequest_oauthType이_UNRECOGNIZED이면_예외발생() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("valid-token")
                .setDeviceId("device-1")
                .setOauthTypeValue(999)
                .build();

        assertThatThrownBy(() -> validator.validateAuthorizationRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("oauthType is invalid");
    }

    @Test
    void validateAuthorizationRequest_정상_요청이면_예외없이_통과() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("valid-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.GOOGLE)
                .build();

        assertThatCode(() -> validator.validateAuthorizationRequest(request))
                .doesNotThrowAnyException();
    }

    @Test
    void validateAuthorizationRequest_rawNonce가_256자를_넘으면_예외발생() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("valid-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.APPLE)
                .setRawNonce("a".repeat(257))
                .build();

        assertThatThrownBy(() -> validator.validateAuthorizationRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rawNonce must not exceed 256 characters");
    }

    @Test
    void validateAuthorizationRequest_rawNonce가_256자이면_통과() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("valid-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.APPLE)
                .setRawNonce("a".repeat(256))
                .build();

        assertThatCode(() -> validator.validateAuthorizationRequest(request))
                .doesNotThrowAnyException();
    }

    @Test
    void validateAuthorizationRequest_APPLE인데_rawNonce가_없어도_통과() {
        // 필수 여부는 어댑터가 UNAUTHENTICATED 로 판단한다. validator 가 막으면 status 가 바뀐다.
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("valid-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.APPLE)
                .build();

        assertThatCode(() -> validator.validateAuthorizationRequest(request))
                .doesNotThrowAnyException();
    }
}
