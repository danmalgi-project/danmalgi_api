package com.danmalgi.backend.friendship.grpc.validator;

import org.springframework.stereotype.Component;

import com.danmalgi.backend.external.friend.v1.FriendProto;

@Component
public class FriendshipGrpcValidator {
    public void validateUpdateFriendStatusRequest(long friendshipId, FriendProto.FriendStatus friendStatus) {
        if (friendshipId <= 0) {
            throw new IllegalArgumentException("friendshipId must be positive");
        }

        if (friendStatus == FriendProto.FriendStatus.UNRECOGNIZED) {
            throw new IllegalArgumentException("friendStatus is invalid");
        }
    }
}
