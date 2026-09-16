package com.danmalgi.backend.udmc.service;

import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceGetChannelNamesTest {

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
    void getChannelNames_channelId와_userIds로_조회하면_userId별_채널명을_반환한다() {
        Long channelId = 1L;
        List<Long> userIds = List.of(10L, 20L);

        UserEntity user10 = UserEntity.from(new User(10L, "a@a.com", "Alice", "00001", null, "id1", 1, 0));
        UserEntity user20 = UserEntity.from(new User(20L, "b@b.com", "Bob", "00002", null, "id2", 1, 0));
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder().id(channelId).isGroup(false).build();
        UserDirectMessageChannelEntity e1 = UserDirectMessageChannelEntity.of(user10, channel, "Bob");
        UserDirectMessageChannelEntity e2 = UserDirectMessageChannelEntity.of(user20, channel, "Alice");

        when(udmcJpaRepository.findAllByDirectMessageChannel_IdAndUser_IdIn(channelId, userIds))
                .thenReturn(List.of(e1, e2));

        Map<Long, String> result = service.getChannelNames(channelId, userIds);

        assertThat(result).containsEntry(10L, "Bob").containsEntry(20L, "Alice");
    }

    @Test
    void getChannelNames_해당하는_유저가_없으면_빈_맵을_반환한다() {
        Long channelId = 1L;
        List<Long> userIds = List.of(99L);

        when(udmcJpaRepository.findAllByDirectMessageChannel_IdAndUser_IdIn(channelId, userIds))
                .thenReturn(List.of());

        Map<Long, String> result = service.getChannelNames(channelId, userIds);

        assertThat(result).isEmpty();
    }
}
