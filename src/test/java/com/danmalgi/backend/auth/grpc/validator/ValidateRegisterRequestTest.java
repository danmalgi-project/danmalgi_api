package com.danmalgi.backend.auth.grpc.validator;

import com.danmalgi.backend.external.auth.v1.AuthProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateRegisterRequestTest {

    private final AuthGrpcValidator validator = new AuthGrpcValidator();

    @Test
    void validateRegisterRequest_nickname이_blank이면_예외발생() {
        AuthProto.RegisterRequest request = AuthProto.RegisterRequest.newBuilder()
                .setNickname("")
                .setTag("12345")
                .build();

        assertThatThrownBy(() -> validator.validateRegisterRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("nickname must not be blank");
    }

    @Test
    void validateRegisterRequest_tag가_blank이면_예외발생() {
        AuthProto.RegisterRequest request = AuthProto.RegisterRequest.newBuilder()
                .setNickname("홍길동")
                .setTag("")
                .build();

        assertThatThrownBy(() -> validator.validateRegisterRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tag must not be blank");
    }

    @Test
    void validateRegisterRequest_정상_요청이면_예외없이_통과() {
        AuthProto.RegisterRequest request = AuthProto.RegisterRequest.newBuilder()
                .setNickname("홍길동")
                .setTag("12345")
                .build();

        assertThatCode(() -> validator.validateRegisterRequest(request))
                .doesNotThrowAnyException();
    }
}
