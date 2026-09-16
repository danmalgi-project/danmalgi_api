package com.danmalgi.backend.user.grpc.validator;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.external.user.v1.UserProto;
import com.google.protobuf.ByteString;

class UserGrpcValidatorTest {

    private final UserGrpcValidator validator = new UserGrpcValidator();

    @Test
    void validateUploadProfileRequest_정상이미지는_예외없음() {
        // given
        UserProto.UploadProfileRequest request = UserProto.UploadProfileRequest.newBuilder()
                .setImage(ByteString.copyFrom("valid-image".getBytes()))
                .setExtension("png")
                .build();

        // when & then
        assertThatNoException().isThrownBy(() -> validator.validateUploadProfileRequest(request));
    }

    @Test
    void validateUploadProfileRequest_이미지가_비어있으면_예외발생() {
        // given
        UserProto.UploadProfileRequest request = UserProto.UploadProfileRequest.newBuilder()
                .setImage(ByteString.EMPTY)
                .setExtension("png")
                .build();

        // when & then
        assertThatThrownBy(() -> validator.validateUploadProfileRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image must not be empty");
    }

    @Test
    void validateUploadProfileRequest_이미지가_5MB초과이면_예외발생() {
        // given
        byte[] oversizedImage = new byte[5 * 1024 * 1024 + 1];
        UserProto.UploadProfileRequest request = UserProto.UploadProfileRequest.newBuilder()
                .setImage(ByteString.copyFrom(oversizedImage))
                .setExtension("png")
                .build();

        // when & then
        assertThatThrownBy(() -> validator.validateUploadProfileRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image size must not exceed 5MB");
    }

    @Test
    void validateUploadProfileRequest_extension이_비어있으면_예외발생() {
        // given
        UserProto.UploadProfileRequest request = UserProto.UploadProfileRequest.newBuilder()
                .setImage(ByteString.copyFrom("valid-image".getBytes()))
                .setExtension("")
                .build();

        // when & then
        assertThatThrownBy(() -> validator.validateUploadProfileRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extension must not be blank");
    }

    @Test
    void validateUploadProfileRequest_허용되지않은_extension이면_예외발생() {
        // given
        UserProto.UploadProfileRequest request = UserProto.UploadProfileRequest.newBuilder()
                .setImage(ByteString.copyFrom("valid-image".getBytes()))
                .setExtension("bmp")
                .build();

        // when & then
        assertThatThrownBy(() -> validator.validateUploadProfileRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extension must be one of");
    }
}
