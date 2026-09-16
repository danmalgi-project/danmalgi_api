package com.danmalgi.backend.auth.grpc.validator;

import com.danmalgi.backend.external.auth.v1.AuthProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateUpsertFcmTokenRequestTest {

    private final AuthGrpcValidator validator = new AuthGrpcValidator();

    @Test
    void validateUpsertFcmTokenRequest_fcmToken이_blank이면_예외발생() {
        AuthProto.UpsertFcmTokenRequest request = AuthProto.UpsertFcmTokenRequest.newBuilder()
                .setFcmToken("")
                .build();

        assertThatThrownBy(() -> validator.validateUpsertFcmTokenRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fcmToken must not be blank");
    }

    @Test
    void validateUpsertFcmTokenRequest_정상_요청이면_예외없이_통과() {
        AuthProto.UpsertFcmTokenRequest request = AuthProto.UpsertFcmTokenRequest.newBuilder()
                .setFcmToken("fcm-token-123")
                .build();

        assertThatCode(() -> validator.validateUpsertFcmTokenRequest(request))
                .doesNotThrowAnyException();
    }
}
