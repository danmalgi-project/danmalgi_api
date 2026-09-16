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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceGetFriendsTest {

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
    void getFriends_친구_목록_반환() {
        UserEntity friendEntity = UserFixture.createUserEntity(2L, "Bob", "00002");
        FriendshipEntity friendshipEntity = mock(FriendshipEntity.class);
        when(friendshipEntity.getId()).thenReturn(1L);
        when(friendshipEntity.getFriend()).thenReturn(friendEntity);
        when(friendshipEntity.getStatus()).thenReturn(FriendStatus.ACCEPT.getNumber());

        when(friendshipJpaRepository.findAllByUserId(1L)).thenReturn(List.of(friendshipEntity));

        List<Friendship> result = friendshipService.getFriends(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFriend().getName()).isEqualTo("Bob");
        assertThat(result.get(0).getStatus()).isEqualTo(FriendStatus.ACCEPT);
    }

    @Test
    void getFriends_결과가_없으면_빈_목록_반환() {
        when(friendshipJpaRepository.findAllByUserId(1L)).thenReturn(List.of());

        List<Friendship> result = friendshipService.getFriends(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getFriends_friend의_profileImageUrl이_path이면_public_URL로_치환된다() {
        UserEntity friendEntity = UserFixture.createUserEntity(2L, "Bob", "00002");
        friendEntity.updateProfileImageUrl("profiles/2/img.webp");

        FriendshipEntity friendshipEntity = mock(FriendshipEntity.class);
        when(friendshipEntity.getId()).thenReturn(1L);
        when(friendshipEntity.getFriend()).thenReturn(friendEntity);
        when(friendshipEntity.getStatus()).thenReturn(FriendStatus.ACCEPT.getNumber());

        when(friendshipJpaRepository.findAllByUserId(1L)).thenReturn(List.of(friendshipEntity));
        when(r2Uploader.toPublicUrl("profiles/2/img.webp"))
                .thenReturn("https://cdn.example.com/profiles/2/img.webp");

        List<Friendship> result = friendshipService.getFriends(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFriend().getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/2/img.webp");
        verify(r2Uploader).toPublicUrl("profiles/2/img.webp");
    }

    @Test
    void getFriends_friend의_profileImageUrl이_null이면_R2Uploader_미호출() {
        UserEntity friendEntity = UserFixture.createUserEntity(2L, "Bob", "00002");

        FriendshipEntity friendshipEntity = mock(FriendshipEntity.class);
        when(friendshipEntity.getId()).thenReturn(1L);
        when(friendshipEntity.getFriend()).thenReturn(friendEntity);
        when(friendshipEntity.getStatus()).thenReturn(FriendStatus.ACCEPT.getNumber());

        when(friendshipJpaRepository.findAllByUserId(1L)).thenReturn(List.of(friendshipEntity));

        List<Friendship> result = friendshipService.getFriends(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFriend().getProfileImageUrl()).isNull();
        verifyNoInteractions(r2Uploader);
    }
}
