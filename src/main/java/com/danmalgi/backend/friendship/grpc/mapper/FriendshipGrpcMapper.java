package com.danmalgi.backend.friendship.grpc.mapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.danmalgi.backend.external.friend.v1.FriendProto;
import com.danmalgi.backend.friendship.domain.model.Friendship;
import com.danmalgi.backend.user.grpc.mapper.UserGrpcMapper;

public class FriendshipGrpcMapper {
    private FriendshipGrpcMapper() {}

    public static List<FriendProto.Friend> toProtoFriends(List<Friendship> friendships) {
        if (friendships == null || friendships.isEmpty()) {
            return Collections.emptyList();
        }

        return friendships.stream()
                .map(FriendshipGrpcMapper::toProtoFriend)
                .collect(Collectors.toList());
    }

    public static FriendProto.Friend toProtoFriend(Friendship friendship) {
        FriendProto.FriendStatus friendStatus = FriendProto.FriendStatus.forNumber(friendship.getStatus().getNumber());
        if (friendStatus == null) {
            throw new IllegalStateException("invalid friend status: " + friendship.getStatus());
        }

        return FriendProto.Friend.newBuilder()
                .setId(Optional.ofNullable(friendship.getId()).orElse(0L))
                .setUser(UserGrpcMapper.toProtoUser(friendship.getFriend()))
                .setStatus(friendStatus)
                .build();
    }
}
