package com.danmalgi.backend.user.grpc.validator;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.danmalgi.backend.external.user.v1.UserProto;

class ValidateSearchUserRequestTest {

    private final UserGrpcValidator validator = new UserGrpcValidator();

    @Test
    void validateSearchUserRequest_정상_name_tag면_예외없음() {
        UserProto.SearchUserRequest request = UserProto.SearchUserRequest.newBuilder()
                .setName("홍길동")
                .setTag("0001")
                .build();

        assertThatNoException().isThrownBy(() -> validator.validateSearchUserRequest(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void validateSearchUserRequest_name이_공백이면_예외발생(String name) {
        UserProto.SearchUserRequest request = UserProto.SearchUserRequest.newBuilder()
                .setName(name)
                .setTag("0001")
                .build();

        assertThatThrownBy(() -> validator.validateSearchUserRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void validateSearchUserRequest_tag가_공백이면_예외발생(String tag) {
        UserProto.SearchUserRequest request = UserProto.SearchUserRequest.newBuilder()
                .setName("홍길동")
                .setTag(tag)
                .build();

        assertThatThrownBy(() -> validator.validateSearchUserRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tag must not be blank");
    }
}
