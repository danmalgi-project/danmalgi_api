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
    void leaveChannel_호출자의_참여행만_삭제한다() {
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

    // 이 메서드는 그룹 전용 경로라 DMC 를 건드리지 않는다.
    // 참여자가 0명이 되는 그룹의 채널 삭제는 호출자인 DirectMessageService.leaveChannel 책임이고,
    // 1:1 은 애초에 이 메서드를 타지 않는다 (lockAndDeleteOneToOneMemberships 로 간다).
    @Test
    void leaveChannel_마지막_참여자가_나가도_채널행은_건드리지_않는다() {
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
