package com.danmalgi.backend.friendship.service;

import com.danmalgi.backend.friendship.domain.model.FriendStatus;
import com.danmalgi.backend.friendship.domain.model.Friendship;
import com.danmalgi.backend.friendship.repository.entity.FriendshipEntity;
import com.danmalgi.backend.friendship.repository.persistence.FriendshipJpaRepository;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.user.fixture.UserFixture;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceUpdateFriendTest {

    @Mock
    private FriendshipJpaRepository friendshipJpaRepository;

    @Mock
    private R2Uploader r2Uploader;

    private FriendshipService friendshipService;

    @BeforeEach
    void setUp() {
        friendshipService = new FriendshipService(friendshipJpaRepository, r2Uploader);
    }

    @Test
    void updateFriend_friendship이_없으면_예외발생() {
        when(friendshipJpaRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.updateFriend(1L, 99L, FriendStatus.BLOCK.getNumber()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("friendship not found");
    }

    @Test
    void updateFriend_정상_요청이면_상태_업데이트_후_목록_반환() {
        UserEntity friendEntity = UserFixture.createUserEntity(2L, "Bob", "00002");
        FriendshipEntity friendshipEntity = mock(FriendshipEntity.class);
        when(friendshipEntity.getId()).thenReturn(1L);
        when(friendshipEntity.getFriend()).thenReturn(friendEntity);
        when(friendshipEntity.getStatus()).thenReturn(FriendStatus.ACCEPT.getNumber());

        when(friendshipJpaRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(friendshipEntity));
        when(friendshipJpaRepository.save(friendshipEntity)).thenReturn(friendshipEntity);
        when(friendshipJpaRepository.findAllByUserId(1L)).thenReturn(List.of(friendshipEntity));

        friendshipService.updateFriend(1L, 1L, FriendStatus.BLOCK.getNumber());

        verify(friendshipEntity).updateStatus(FriendStatus.BLOCK.getNumber());
        verify(friendshipJpaRepository).save(friendshipEntity);
    }

    @Test
    void updateFriend_friend의_profileImageUrl이_path이면_public_URL로_치환된다() {
        UserEntity friendEntity = UserFixture.createUserEntity(2L, "Bob", "00002");
        friendEntity.updateProfileImageUrl("profiles/2/img.webp");

        FriendshipEntity friendshipEntity = mock(FriendshipEntity.class);
        when(friendshipEntity.getId()).thenReturn(1L);
        when(friendshipEntity.getFriend()).thenReturn(friendEntity);
        when(friendshipEntity.getStatus()).thenReturn(FriendStatus.ACCEPT.getNumber());

        when(friendshipJpaRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(friendshipEntity));
        when(friendshipJpaRepository.save(friendshipEntity)).thenReturn(friendshipEntity);
        when(friendshipJpaRepository.findAllByUserId(1L)).thenReturn(List.of(friendshipEntity));
        when(r2Uploader.toPublicUrl("profiles/2/img.webp"))
                .thenReturn("https://cdn.example.com/profiles/2/img.webp");

        List<Friendship> result = friendshipService.updateFriend(1L, 1L, FriendStatus.BLOCK.getNumber());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFriend().getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/2/img.webp");
        verify(r2Uploader).toPublicUrl("profiles/2/img.webp");
    }
}
