package com.danmalgi.backend.friendship.grpc;

import com.danmalgi.backend.external.friend.v1.FriendProto;
import com.danmalgi.backend.friendship.domain.model.FriendStatus;
import com.danmalgi.backend.friendship.domain.model.Friendship;
import com.danmalgi.backend.friendship.grpc.validator.FriendshipGrpcValidator;
import com.danmalgi.backend.friendship.service.FriendshipService;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipGrpcUpdateFriendStatusTest {

    @Mock
    private FriendshipGrpcValidator friendshipGrpcValidator;

    @Mock
    private FriendshipService friendshipService;

    @Mock
    private StreamObserver<FriendProto.UpdateFriendStatusResponse> responseObserver;

    private FriendshipGrpc friendshipGrpc;

    @BeforeEach
    void setUp() {
        friendshipGrpc = new FriendshipGrpc(friendshipGrpcValidator, friendshipService);
    }

    @Test
    void updateFriendStatus_validator_예외발생시_예외_전파() {
        FriendProto.UpdateFriendStatusRequest request = FriendProto.UpdateFriendStatusRequest.newBuilder()
                .setFriendshipId(0L)
                .setFriendStatus(FriendProto.FriendStatus.BLOCK)
                .build();
        doThrow(new IllegalArgumentException("friendshipId must be positive"))
                .when(friendshipGrpcValidator).validateUpdateFriendStatusRequest(0L, FriendProto.FriendStatus.BLOCK);

        assertThatThrownBy(() -> friendshipGrpc.updateFriendStatus(request, responseObserver))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateFriendStatus_정상_요청이면_onNext와_onCompleted_호출() {
        FriendProto.UpdateFriendStatusRequest request = FriendProto.UpdateFriendStatusRequest.newBuilder()
                .setFriendshipId(1L)
                .setFriendStatus(FriendProto.FriendStatus.BLOCK)
                .build();
        // Service 가 이미 presigned URL 적용된 friend 를 반환한다고 가정
        User friend = new User(2L, "b@b.com", "Bob", "00002", null, "id2",
                OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber());
        friend.setProfileImageUrl("https://signed.example.com/profiles/2/img?sig=xyz");

        when(friendshipService.updateFriend(1L, 1L, FriendStatus.BLOCK.getNumber()))
                .thenReturn(List.of(Friendship.builder().id(1L).friend(friend).status(FriendStatus.BLOCK).build()));

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> friendshipGrpc.updateFriendStatus(request, responseObserver));

        verify(responseObserver).onNext(any());
        verify(responseObserver).onCompleted();
    }
}
