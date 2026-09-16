package com.danmalgi.backend.udmc.service;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceCreateUserChannelTest {

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
    void createUserChannel_참여자별_UserDirectMessageChannel_저장하고_요청자_DirectMessage_반환() {
        Long userId = 1L;
        Long friendId = 2L;
        Long channelId = 10L;

        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder().id(channelId).isGroup(false).build();
        UserEntity userEntity = UserEntity.from(new User(userId, "a@a.com", "Alice", "00001", null, "id1", 1, 0));
        UserEntity friendEntity = UserEntity.from(new User(friendId, "b@b.com", "Bob", "00002", null, "id2", 1, 0));

        when(dmcJpaRepository.getReferenceById(channelId)).thenReturn(channel);
        when(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity);
        when(userJpaRepository.getReferenceById(friendId)).thenReturn(friendEntity);
        when(udmcJpaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        DirectMessage result = service.createUserChannel(userId, List.of(friendId), channelId);

        assertThat(result.getId()).isEqualTo(channelId);
        assertThat(result.getChannelName()).isEqualTo("Bob");
        assertThat(result.getIsGroup()).isFalse();
    }

    @Test
    void createUserChannel_채널명은_자신을_제외한_참여자_이름을_콤마로_조합() {
        Long userId = 1L;
        Long friendId1 = 2L;
        Long friendId2 = 3L;
        Long channelId = 10L;

        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder().id(channelId).isGroup(true).build();
        UserEntity userEntity = UserEntity.from(new User(userId, "a@a.com", "Alice", "00001", null, "id1", 1, 0));
        UserEntity friend1Entity = UserEntity.from(new User(friendId1, "b@b.com", "Bob", "00002", null, "id2", 1, 0));
        UserEntity friend2Entity = UserEntity.from(new User(friendId2, "c@c.com", "Carol", "00003", null, "id3", 1, 0));

        when(dmcJpaRepository.getReferenceById(channelId)).thenReturn(channel);
        when(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity);
        when(userJpaRepository.getReferenceById(friendId1)).thenReturn(friend1Entity);
        when(userJpaRepository.getReferenceById(friendId2)).thenReturn(friend2Entity);
        when(udmcJpaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        DirectMessage result = service.createUserChannel(userId, List.of(friendId1, friendId2), channelId);

        assertThat(result.getChannelName()).isEqualTo("Bob,Carol");
        assertThat(result.getIsGroup()).isTrue();
    }

    @Test
    void createUserChannel_모든_참여자의_UserDirectMessageChannel을_saveAll로_저장() {
        Long userId = 1L;
        Long friendId = 2L;
        Long channelId = 10L;

        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder().id(channelId).isGroup(false).build();
        UserEntity userEntity = UserEntity.from(new User(userId, "a@a.com", "Alice", "00001", null, "id1", 1, 0));
        UserEntity friendEntity = UserEntity.from(new User(friendId, "b@b.com", "Bob", "00002", null, "id2", 1, 0));

        when(dmcJpaRepository.getReferenceById(channelId)).thenReturn(channel);
        when(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity);
        when(userJpaRepository.getReferenceById(friendId)).thenReturn(friendEntity);
        when(udmcJpaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.createUserChannel(userId, List.of(friendId), channelId);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(udmcJpaRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void createUserChannel_channelImageUrl이_반환된다() {
        Long userId = 1L;
        Long friendId = 2L;
        Long channelId = 10L;

        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder()
                .id(channelId)
                .isGroup(false)
                .channelImageUrl(null)
                .build();
        UserEntity userEntity = UserEntity.from(new User(userId, "a@a.com", "Alice", "00001", null, "id1", 1, 0));
        UserEntity friendEntity = UserEntity.from(new User(friendId, "b@b.com", "Bob", "00002", null, "id2", 1, 0));

        when(dmcJpaRepository.getReferenceById(channelId)).thenReturn(channel);
        when(userJpaRepository.getReferenceById(userId)).thenReturn(userEntity);
        when(userJpaRepository.getReferenceById(friendId)).thenReturn(friendEntity);
        when(udmcJpaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        DirectMessage result = service.createUserChannel(userId, List.of(friendId), channelId);

        assertThat(result.getChannelImageUrl()).isNull();
    }
}
