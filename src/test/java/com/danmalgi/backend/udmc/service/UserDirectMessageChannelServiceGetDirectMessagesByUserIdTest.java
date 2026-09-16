package com.danmalgi.backend.udmc.service;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceGetDirectMessagesByUserIdTest {

    @Mock
    private UserDirectMessageChannelJpaRepository udmcJpaRepository;
    @Mock
    private DirectMessageChannelJpaRepository dmcJpaRepository;
    @Mock
    private UserJpaRepository userJpaRepository;
    private UserDirectMessageChannelService service;

    @BeforeEach
    void setUp() {
        service = new UserDirectMessageChannelService(udmcJpaRepository, dmcJpaRepository, userJpaRepository);
    }

    @Test
    void getDirectMessagesByUserId_userId로_조회하면_DirectMessage_리스트_반환() {
        Long userId = 1L;
        UserEntity user = UserEntity.from(new User(userId, "a@a.com", "Alice", "00001", null, "id1", 1, 0));
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder().id(10L).isGroup(false).build();
        UserDirectMessageChannelEntity entity = UserDirectMessageChannelEntity.of(user, channel, "Bob");

        when(udmcJpaRepository.findAllByUserId(userId)).thenReturn(List.of(entity));

        List<DirectMessage> result = service.getDirectMessagesByUserId(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getChannelName()).isEqualTo("Bob");
        assertThat(result.get(0).getIsGroup()).isFalse();
    }

    @Test
    void getDirectMessagesByUserId_그룹채널이면_isGroup이_true() {
        Long userId = 1L;
        UserEntity user = UserEntity.from(new User(userId, "a@a.com", "Alice", "00001", null, "id1", 1, 0));
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder().id(10L).isGroup(true).build();
        UserDirectMessageChannelEntity entity = UserDirectMessageChannelEntity.of(user, channel, "Bob,Carol");

        when(udmcJpaRepository.findAllByUserId(userId)).thenReturn(List.of(entity));

        List<DirectMessage> result = service.getDirectMessagesByUserId(userId);

        assertThat(result.get(0).getIsGroup()).isTrue();
    }

    @Test
    void getDirectMessagesByUserId_조회결과가_없으면_빈_리스트_반환() {
        Long userId = 99L;
        when(udmcJpaRepository.findAllByUserId(userId)).thenReturn(List.of());

        List<DirectMessage> result = service.getDirectMessagesByUserId(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void getDirectMessagesByUserId_채널이미지url도_함께_반환() {
        Long userId = 1L;
        UserEntity user = UserEntity.from(new User(userId, "a@a.com", "Alice", "00001", null, "id1", 1, 0));
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder()
                .id(10L)
                .isGroup(true)
                .channelImageUrl("channels/10/abc.png")
                .build();
        UserDirectMessageChannelEntity entity = UserDirectMessageChannelEntity.of(user, channel, "Bob,Carol");

        when(udmcJpaRepository.findAllByUserId(userId)).thenReturn(List.of(entity));

        List<DirectMessage> result = service.getDirectMessagesByUserId(userId);

        assertThat(result.get(0).getChannelImageUrl()).isEqualTo("channels/10/abc.png");
    }
}
