package com.danmalgi.backend.dm.grpc.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectMessageGrpcValidatorValidateCreateDirectMessageChannelRequestTest {

    private static final Long REQUESTER_ID = 1L;

    private DirectMessageGrpcValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DirectMessageGrpcValidator();
    }

    private DirectMessageProto.CreateDirectMessageChannelRequest requestOf(List<Long> friendIds) {
        return DirectMessageProto.CreateDirectMessageChannelRequest.newBuilder()
                .addAllFriendIds(friendIds)
                .build();
    }

    @Test
    void 정상_1대1_요청이면_예외를_던지지_않는다() {
        assertThatCode(() -> validator.validateCreateDirectMessageChannelRequest(
                requestOf(List.of(2L)), REQUESTER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void 정상_그룹_요청이면_예외를_던지지_않는다() {
        assertThatCode(() -> validator.validateCreateDirectMessageChannelRequest(
                requestOf(List.of(2L, 3L)), REQUESTER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void friend_ids가_비어있으면_IllegalArgumentException() {
        DirectMessageProto.CreateDirectMessageChannelRequest request = requestOf(List.of());

        assertThatThrownBy(() -> validator.validateCreateDirectMessageChannelRequest(request, REQUESTER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("friend_ids");
    }

    @Test
    void friend_ids에_요청자_자신이_포함되면_IllegalArgumentException() {
        DirectMessageProto.CreateDirectMessageChannelRequest request = requestOf(List.of(2L, REQUESTER_ID));

        assertThatThrownBy(() -> validator.validateCreateDirectMessageChannelRequest(request, REQUESTER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("friend_ids");
    }

    @Test
    void friend_ids가_요청자_자신_하나뿐이어도_IllegalArgumentException() {
        DirectMessageProto.CreateDirectMessageChannelRequest request = requestOf(List.of(REQUESTER_ID));

        assertThatThrownBy(() -> validator.validateCreateDirectMessageChannelRequest(request, REQUESTER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("friend_ids");
    }

    @Test
    void friend_ids에_중복_id가_있으면_IllegalArgumentException() {
        DirectMessageProto.CreateDirectMessageChannelRequest request = requestOf(List.of(2L, 2L));

        assertThatThrownBy(() -> validator.validateCreateDirectMessageChannelRequest(request, REQUESTER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("friend_ids");
    }
}
