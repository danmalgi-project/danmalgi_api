package com.danmalgi.backend.friendship.grpc.validator;

import com.danmalgi.backend.external.friend.v1.FriendProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FriendshipGrpcValidatorValidateUpdateFriendStatusRequestTest {

    private final FriendshipGrpcValidator validator = new FriendshipGrpcValidator();

    @Test
    void validateUpdateFriendStatusRequest_정상_요청이면_예외없음() {
        assertThatNoException().isThrownBy(() ->
                validator.validateUpdateFriendStatusRequest(1L, FriendProto.FriendStatus.BLOCK)
        );
    }

    @Test
    void validateUpdateFriendStatusRequest_friendshipId가_0이면_예외발생() {
        assertThatThrownBy(() ->
                validator.validateUpdateFriendStatusRequest(0L, FriendProto.FriendStatus.BLOCK)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("friendshipId");
    }

    @Test
    void validateUpdateFriendStatusRequest_friendshipId가_음수이면_예외발생() {
        assertThatThrownBy(() ->
                validator.validateUpdateFriendStatusRequest(-1L, FriendProto.FriendStatus.BLOCK)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("friendshipId");
    }

    @Test
    void validateUpdateFriendStatusRequest_status가_UNRECOGNIZED이면_예외발생() {
        assertThatThrownBy(() ->
                validator.validateUpdateFriendStatusRequest(1L, FriendProto.FriendStatus.UNRECOGNIZED)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("friendStatus");
    }
}
