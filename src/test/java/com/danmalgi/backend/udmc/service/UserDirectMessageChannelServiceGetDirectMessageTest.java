package com.danmalgi.backend.udmc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceGetDirectMessageTest {

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
    void getDirectMessage_returnsCallerViewAndParticipants() {
        Long userId = 1L;
        Long friendId = 2L;
        Long channelId = 10L;

        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder()
                .id(channelId)
                .isGroup(true)
                .channelImageUrl("channels/10/abc.webp")
                .build();
        UserEntity userEntity = UserEntity.from(new User(userId, "a@a.com", "Alice", "00001", null, "id1", 1, 0));
        UserEntity friendEntity = UserEntity.from(new User(friendId, "b@b.com", "Bob", "00002", null, "id2", 1, 0));
        UserDirectMessageChannelEntity myMembership =
                UserDirectMessageChannelEntity.of(userEntity, channel, "Study Room");
        UserDirectMessageChannelEntity friendMembership =
                UserDirectMessageChannelEntity.of(friendEntity, channel, "Study Room");

        when(udmcJpaRepository.findByUser_IdAndDirectMessageChannel_Id(userId, channelId))
                .thenReturn(Optional.of(myMembership));
        when(udmcJpaRepository.findAllByDirectMessageChannelId(channelId))
                .thenReturn(List.of(myMembership, friendMembership));

        DirectMessage result = service.getDirectMessage(userId, channelId);

        assertThat(result.getId()).isEqualTo(channelId);
        assertThat(result.getChannelName()).isEqualTo("Study Room");
        assertThat(result.getIsGroup()).isTrue();
        assertThat(result.getChannelImageUrl()).isEqualTo("channels/10/abc.webp");
        assertThat(result.getUsers())
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }
}
