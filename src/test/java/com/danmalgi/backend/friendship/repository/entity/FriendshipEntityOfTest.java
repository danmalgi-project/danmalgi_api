package com.danmalgi.backend.friendship.repository.entity;

import com.danmalgi.backend.friendship.domain.model.FriendStatus;
import com.danmalgi.backend.user.fixture.UserFixture;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FriendshipEntityOfTest {

    @Test
    void of_유저와_친구와_상태로_FriendshipEntity를_생성한다() {
        UserEntity user = UserFixture.createUserEntity(1L, "Alice", "00001");
        UserEntity friend = UserFixture.createUserEntity(2L, "Bob", "00002");

        FriendshipEntity entity = FriendshipEntity.of(user, friend, FriendStatus.ACCEPT.getNumber());

        assertThat(entity.getUser()).isEqualTo(user);
        assertThat(entity.getFriend()).isEqualTo(friend);
        assertThat(entity.getStatus()).isEqualTo(FriendStatus.ACCEPT.getNumber());
    }
}
