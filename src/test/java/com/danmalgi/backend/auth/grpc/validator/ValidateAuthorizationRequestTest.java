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
}
