package com.danmalgi.backend.udmc.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceLeaveChannelTest {

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
    void leaveChannel_deletesOnlyCallerMembership() {
        Long userId = 1L;
        Long channelId = 10L;
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder().id(channelId).isGroup(true).build();
        UserDirectMessageChannelEntity membership =
                UserDirectMessageChannelEntity.of(mock(UserEntity.class), channel, "Study Room");

        when(udmcJpaRepository.findByUser_IdAndDirectMessageChannel_Id(userId, channelId))
                .thenReturn(Optional.of(membership));

        service.leaveChannel(userId, channelId);

        verify(udmcJpaRepository).delete(membership);
    }

    @Test
    void leaveChannel_keepsChannelRowWhenLastMemberLeaves() {
        Long userId = 1L;
        Long channelId = 10L;
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder().id(channelId).isGroup(true).build();
        UserDirectMessageChannelEntity membership =
                UserDirectMessageChannelEntity.of(mock(UserEntity.class), channel, "Study Room");

        when(udmcJpaRepository.findByUser_IdAndDirectMessageChannel_Id(userId, channelId))
                .thenReturn(Optional.of(membership));

        assertThatCode(() -> service.leaveChannel(userId, channelId)).doesNotThrowAnyException();

        verify(udmcJpaRepository).delete(membership);
        verifyNoInteractions(dmcJpaRepository);
    }
}
