package com.danmalgi.backend.dm.grpc.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectMessageGrpcValidatorValidateGetDirectMessageChannelRequestTest {

    private DirectMessageGrpcValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DirectMessageGrpcValidator();
    }

    @Test
    void validateGetDirectMessageChannelRequest_dmId가_양수이면_통과() {
        DirectMessageProto.GetDirectMessageChannelRequest request = request(10L);

        assertThatCode(() -> validator.validateGetDirectMessageChannelRequest(request))
                .doesNotThrowAnyException();
    }

    @Test
    void validateGetDirectMessageChannelRequest_dmId가_0이면_IllegalArgumentException() {
        DirectMessageProto.GetDirectMessageChannelRequest request = request(0L);

        assertThatThrownBy(() -> validator.validateGetDirectMessageChannelRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dm_id");
    }

    @Test
    void validateGetDirectMessageChannelRequest_dmId가_음수이면_IllegalArgumentException() {
        DirectMessageProto.GetDirectMessageChannelRequest request = request(-1L);

        assertThatThrownBy(() -> validator.validateGetDirectMessageChannelRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dm_id");
    }

    private DirectMessageProto.GetDirectMessageChannelRequest request(long dmId) {
        return DirectMessageProto.GetDirectMessageChannelRequest.newBuilder()
                .setDmId(dmId)
                .build();
    }
}
