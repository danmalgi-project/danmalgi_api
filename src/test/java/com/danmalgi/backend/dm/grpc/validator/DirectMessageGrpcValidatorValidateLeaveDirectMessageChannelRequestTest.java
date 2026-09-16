package com.danmalgi.backend.dm.grpc.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectMessageGrpcValidatorValidateLeaveDirectMessageChannelRequestTest {

    private DirectMessageGrpcValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DirectMessageGrpcValidator();
    }

    @Test
    void validateLeaveDirectMessageChannelRequest_acceptsPositiveDmId() {
        DirectMessageProto.LeaveDirectMessageChannelRequest request =
                DirectMessageProto.LeaveDirectMessageChannelRequest.newBuilder()
                        .setDmId(10L)
                        .build();

        assertThatCode(() -> validator.validateLeaveDirectMessageChannelRequest(request))
                .doesNotThrowAnyException();
    }

    @Test
    void validateLeaveDirectMessageChannelRequest_rejectsNonPositiveDmId() {
        DirectMessageProto.LeaveDirectMessageChannelRequest request =
                DirectMessageProto.LeaveDirectMessageChannelRequest.newBuilder()
                        .setDmId(0L)
                        .build();

        assertThatThrownBy(() -> validator.validateLeaveDirectMessageChannelRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dm_id");
    }
}
