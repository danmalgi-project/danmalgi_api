package com.danmalgi.backend.dm.grpc.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectMessageGrpcValidatorValidateUpdateDirectMessageChannelRequestTest {

    private DirectMessageGrpcValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DirectMessageGrpcValidator();
    }

    @Test
    void validateUpdateDirectMessageChannelRequest_acceptsNameOnlyUpdate() {
        DirectMessageProto.UpdateDirectMessageChannelRequest request =
                DirectMessageProto.UpdateDirectMessageChannelRequest.newBuilder()
                        .setDmId(10L)
                        .setChannelName("Study Room")
                        .build();

        assertThatCode(() -> validator.validateUpdateDirectMessageChannelRequest(request))
                .doesNotThrowAnyException();
    }

    @Test
    void validateUpdateDirectMessageChannelRequest_acceptsImageOnlyUpdate() {
        DirectMessageProto.UpdateDirectMessageChannelRequest request =
                DirectMessageProto.UpdateDirectMessageChannelRequest.newBuilder()
                        .setDmId(10L)
                        .setImage(ByteString.copyFrom(new byte[]{1, 2, 3}))
                        .setExtension("png")
                        .build();

        assertThatCode(() -> validator.validateUpdateDirectMessageChannelRequest(request))
                .doesNotThrowAnyException();
    }

    @Test
    void validateUpdateDirectMessageChannelRequest_rejectsNonPositiveDmId() {
        DirectMessageProto.UpdateDirectMessageChannelRequest request =
                DirectMessageProto.UpdateDirectMessageChannelRequest.newBuilder()
                        .setDmId(0L)
                        .setChannelName("Study Room")
                        .build();

        assertThatThrownBy(() -> validator.validateUpdateDirectMessageChannelRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dm_id");
    }

    @Test
    void validateUpdateDirectMessageChannelRequest_requiresNameOrImage() {
        DirectMessageProto.UpdateDirectMessageChannelRequest request =
                DirectMessageProto.UpdateDirectMessageChannelRequest.newBuilder()
                        .setDmId(10L)
                        .build();

        assertThatThrownBy(() -> validator.validateUpdateDirectMessageChannelRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channel_name or image");
    }

    @Test
    void validateUpdateDirectMessageChannelRequest_requiresExtensionWhenImageExists() {
        DirectMessageProto.UpdateDirectMessageChannelRequest request =
                DirectMessageProto.UpdateDirectMessageChannelRequest.newBuilder()
                        .setDmId(10L)
                        .setImage(ByteString.copyFrom(new byte[]{1, 2, 3}))
                        .build();

        assertThatThrownBy(() -> validator.validateUpdateDirectMessageChannelRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void validateUpdateDirectMessageChannelRequest_rejectsExtensionWithoutImage() {
        DirectMessageProto.UpdateDirectMessageChannelRequest request =
                DirectMessageProto.UpdateDirectMessageChannelRequest.newBuilder()
                        .setDmId(10L)
                        .setExtension("png")
                        .build();

        assertThatThrownBy(() -> validator.validateUpdateDirectMessageChannelRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channel_name or image");
    }
}
