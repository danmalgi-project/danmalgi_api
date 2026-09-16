package com.danmalgi.backend.friendship.grpc.mapper;

import com.danmalgi.backend.external.friend.v1.FriendProto;
import com.danmalgi.backend.friendship.domain.model.FriendStatus;
import com.danmalgi.backend.friendship.domain.model.Friendship;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FriendshipGrpcMapperToProtoFriendTest {

    private User createUser(Long id, String name, String tag) {
        return new User(id, "test@test.com", name, tag, null, "oauth-id",
                OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber());
    }

    @Test
    void toProtoFriend_정상_변환() {
        User friend = createUser(2L, "Bob", "00002");
        Friendship friendship = Friendship.builder()
                .id(1L)
                .friend(friend)
                .status(FriendStatus.ACCEPT)
                .build();

        FriendProto.Friend result = FriendshipGrpcMapper.toProtoFriend(friendship);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus().getNumber()).isEqualTo(FriendStatus.ACCEPT.getNumber());
        assertThat(result.getUser().getName()).isEqualTo("Bob");
    }

    @Test
    void toProtoFriends_빈_목록이면_빈_목록_반환() {
        List<FriendProto.Friend> result = FriendshipGrpcMapper.toProtoFriends(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void toProtoFriends_null이면_빈_목록_반환() {
        List<FriendProto.Friend> result = FriendshipGrpcMapper.toProtoFriends(null);

        assertThat(result).isEmpty();
    }

    @Test
    void toProtoFriends_목록_변환() {
        User friend = createUser(2L, "Bob", "00002");
        List<Friendship> friendships = List.of(
                Friendship.builder().id(1L).friend(friend).status(FriendStatus.ACCEPT).build(),
                Friendship.builder().id(2L).friend(friend).status(FriendStatus.BLOCK).build()
        );

        List<FriendProto.Friend> result = FriendshipGrpcMapper.toProtoFriends(friendships);

        assertThat(result).hasSize(2);
    }
}
