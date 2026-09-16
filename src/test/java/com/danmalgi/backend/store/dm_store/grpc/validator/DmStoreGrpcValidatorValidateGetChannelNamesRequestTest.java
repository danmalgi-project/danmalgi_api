package com.danmalgi.backend.store.dm_store.grpc.validator;

import com.danmalgi.backend.internal.dm_store.v1.DmStoreProto.GetChannelNamesRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DmStoreGrpcValidatorValidateGetChannelNamesRequestTest {

    private DmStoreGrpcValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DmStoreGrpcValidator();
    }

    @Test
    void validateGetChannelNamesRequest_channelId가_양수이면_예외가_발생하지_않는다() {
        GetChannelNamesRequest request = GetChannelNamesRequest.newBuilder()
                .setChannelId(1L)
                .build();

        assertThatCode(() -> validator.validateGetChannelNamesRequest(request))
                .doesNotThrowAnyException();
    }

    @Test
    void validateGetChannelNamesRequest_channelId가_0이면_예외가_발생한다() {
        GetChannelNamesRequest request = GetChannelNamesRequest.newBuilder()
                .setChannelId(0L)
                .build();

        assertThatThrownBy(() -> validator.validateGetChannelNamesRequest(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateGetChannelNamesRequest_channelId가_음수이면_예외가_발생한다() {
        GetChannelNamesRequest request = GetChannelNamesRequest.newBuilder()
                .setChannelId(-1L)
                .build();

        assertThatThrownBy(() -> validator.validateGetChannelNamesRequest(request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
