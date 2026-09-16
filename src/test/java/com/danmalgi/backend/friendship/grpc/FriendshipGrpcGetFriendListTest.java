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
import com.google.protobuf.Empty;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipGrpcGetFriendListTest {

    @Mock
    private FriendshipGrpcValidator friendshipGrpcValidator;

    @Mock
    private FriendshipService friendshipService;

    @Mock
    private StreamObserver<FriendProto.GetFriendListResponse> responseObserver;

    private FriendshipGrpc friendshipGrpc;

    @BeforeEach
    void setUp() {
        friendshipGrpc = new FriendshipGrpc(friendshipGrpcValidator, friendshipService);
    }

    @Test
    void getFriendList_친구_목록_반환() {
        // Service 가 이미 presigned URL 적용된 친구 목록을 반환한다고 가정
        User friend = new User(2L, "b@b.com", "Bob", "00002", null, "id2",
                OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber());
        friend.setProfileImageUrl("https://signed.example.com/profiles/2/img?sig=xyz");
        List<Friendship> friendships = List.of(
                Friendship.builder().id(1L).friend(friend).status(FriendStatus.ACCEPT).build()
        );

        when(friendshipService.getFriends(1L)).thenReturn(friendships);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> friendshipGrpc.getFriendList(Empty.getDefaultInstance(), responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getFriendsCount() == 1 &&
                        response.getFriends(0).getUser().getProfileImageUrl()
                                .equals("https://signed.example.com/profiles/2/img?sig=xyz")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void getFriendList_결과가_없으면_빈_목록으로_응답() {
        when(friendshipService.getFriends(1L)).thenReturn(List.of());

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> friendshipGrpc.getFriendList(Empty.getDefaultInstance(), responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getFriendsCount() == 0
        ));
        verify(responseObserver).onCompleted();
    }
}
