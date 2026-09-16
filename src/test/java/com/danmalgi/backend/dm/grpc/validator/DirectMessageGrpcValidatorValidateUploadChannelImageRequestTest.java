package com.danmalgi.backend.dm.grpc.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectMessageGrpcValidatorValidateUploadChannelImageRequestTest {

    private DirectMessageGrpcValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DirectMessageGrpcValidator();
    }

    private DirectMessageProto.UploadChannelImageRequest.Builder validBuilder() {
        return DirectMessageProto.UploadChannelImageRequest.newBuilder()
                .setDmId(10L)
                .setImage(ByteString.copyFrom(new byte[]{1, 2, 3}))
                .setExtension("png");
    }

    @Test
    void 정상_요청이면_예외를_던지지_않는다() {
        assertThatCode(() -> validator.validateUploadChannelImageRequest(validBuilder().build()))
                .doesNotThrowAnyException();
    }

    @Test
    void dm_id가_0이하면_IllegalArgumentException() {
        DirectMessageProto.UploadChannelImageRequest request = validBuilder().setDmId(0L).build();

        assertThatThrownBy(() -> validator.validateUploadChannelImageRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dm_id");
    }

    @Test
    void image가_비어있으면_IllegalArgumentException() {
        DirectMessageProto.UploadChannelImageRequest request = validBuilder()
                .setImage(ByteString.EMPTY)
                .build();

        assertThatThrownBy(() -> validator.validateUploadChannelImageRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image");
    }

    @Test
    void image가_5MB를_초과하면_IllegalArgumentException() {
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        DirectMessageProto.UploadChannelImageRequest request = validBuilder()
                .setImage(ByteString.copyFrom(tooLarge))
                .build();

        assertThatThrownBy(() -> validator.validateUploadChannelImageRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void extension이_blank면_IllegalArgumentException() {
        DirectMessageProto.UploadChannelImageRequest request = validBuilder().setExtension("").build();

        assertThatThrownBy(() -> validator.validateUploadChannelImageRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void 허용되지_않은_extension이면_IllegalArgumentException() {
        DirectMessageProto.UploadChannelImageRequest request = validBuilder().setExtension("bmp").build();

        assertThatThrownBy(() -> validator.validateUploadChannelImageRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void extension은_대소문자_구분없이_허용된다() {
        DirectMessageProto.UploadChannelImageRequest request = validBuilder().setExtension("PNG").build();

        assertThatCode(() -> validator.validateUploadChannelImageRequest(request))
                .doesNotThrowAnyException();
    }
}
