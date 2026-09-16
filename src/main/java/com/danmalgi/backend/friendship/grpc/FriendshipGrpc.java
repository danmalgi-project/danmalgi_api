package com.danmalgi.backend.friendship.grpc;

import java.util.List;

import org.springframework.grpc.server.service.GrpcService;

import com.danmalgi.backend.external.friend.v1.FriendProto.GetFriendListResponse;
import com.danmalgi.backend.external.friend.v1.FriendProto.UpdateFriendStatusRequest;
import com.danmalgi.backend.external.friend.v1.FriendProto.UpdateFriendStatusResponse;
import com.danmalgi.backend.external.friend.v1.FriendServiceGrpc.FriendServiceImplBase;
import com.danmalgi.backend.friendship.domain.model.Friendship;
import com.danmalgi.backend.friendship.grpc.mapper.FriendshipGrpcMapper;
import com.danmalgi.backend.friendship.grpc.validator.FriendshipGrpcValidator;
import com.danmalgi.backend.friendship.service.FriendshipService;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.google.protobuf.Empty;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@GrpcService
@RequiredArgsConstructor
public class FriendshipGrpc extends FriendServiceImplBase {
    private final FriendshipGrpcValidator friendshipGrpcValidator;
    private final FriendshipService friendshipService;

    @Override
    public void getFriendList(Empty request, StreamObserver<GetFriendListResponse> responseObserver) {
        Long userId = GrpcContext.USER_ID.get();

        List<Friendship> friendList = friendshipService.getFriends(userId);
        GetFriendListResponse.Builder responseBuilder = GetFriendListResponse.newBuilder();

        responseBuilder.addAllFriends(FriendshipGrpcMapper.toProtoFriends(friendList));

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateFriendStatus(UpdateFriendStatusRequest request,
            StreamObserver<UpdateFriendStatusResponse> responseObserver) {
        friendshipGrpcValidator.validateUpdateFriendStatusRequest(
                request.getFriendshipId(),
                request.getFriendStatus()
        );

        Long userId = GrpcContext.USER_ID.get();
        List<Friendship> friendList = friendshipService.updateFriend(
                userId,
                request.getFriendshipId(),
                request.getFriendStatusValue()
        );

        UpdateFriendStatusResponse response = UpdateFriendStatusResponse.newBuilder()
                .addAllFriends(FriendshipGrpcMapper.toProtoFriends(friendList))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
