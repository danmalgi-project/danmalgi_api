package com.danmalgi.backend.udmc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceUpdateChannelNameTest {

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
    void updateChannelName_updatesAllMembershipRows() {
        Long channelId = 10L;
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder().id(channelId).isGroup(true).build();
        List<UserDirectMessageChannelEntity> memberships = List.of(
                UserDirectMessageChannelEntity.of(mock(UserEntity.class), channel, "Old One"),
                UserDirectMessageChannelEntity.of(mock(UserEntity.class), channel, "Old Two")
        );

        when(udmcJpaRepository.findAllByDirectMessageChannelId(channelId)).thenReturn(memberships);
        when(udmcJpaRepository.saveAll(memberships)).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateChannelName(channelId, "Study Room");

        assertThat(memberships)
                .extracting(UserDirectMessageChannelEntity::getChannelName)
                .containsOnly("Study Room");
        verify(udmcJpaRepository).saveAll(memberships);
    }
}
