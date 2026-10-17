package com.danmalgi.backend.dm.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;

import com.danmalgi.backend.chat.client.ChatMessageClient;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.global.infrastructure.image.ImageProcessor;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.service.UserDirectMessageChannelService;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceLeaveChannelTest {

    private static final Long USER_ID = 1L;
    private static final Long CHANNEL_ID = 10L;

    @Mock
    private DirectMessageChannelJpaRepository dmcJpaRepository;
    @Mock
    private UserDirectMessageChannelService udmcService;
    @Mock
    private R2Uploader r2Uploader;
    @Mock
    private ImageProcessor imageProcessor;
    @Mock
    private ChatMessageClient chatMessageClient;

    private DirectMessageService directMessageService;

    @BeforeEach
    void setUp() {
        directMessageService = new DirectMessageService(dmcJpaRepository, udmcService, r2Uploader, imageProcessor, chatMessageClient);
    }

    private DirectMessageChannelEntity givenChannel(boolean isGroup) {
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder()
                .id(CHANNEL_ID)
                .isGroup(isGroup)
                .build();
        UserDirectMessageChannelEntity membership =
                UserDirectMessageChannelEntity.of(mock(UserEntity.class), channel, "상대방");

        when(udmcService.getUserChannel(USER_ID, CHANNEL_ID)).thenReturn(membership);
        return channel;
    }

    @Test
    void leaveChannel_1대1채널이면_참여행_전체와_채널을_삭제한다() {
        DirectMessageChannelEntity channel = givenChannel(false);

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        verify(udmcService).lockAndDeleteOneToOneMemberships(CHANNEL_ID);
        verify(dmcJpaRepository).delete(channel);
    }

    @Test
    void leaveChannel_1대1채널이면_참여행_하나만_지우는_경로를_타지_않는다() {
        givenChannel(false);

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        // 한쪽 행만 지우면 상대 목록에 참여자 1명짜리 방이 남고 재생성 시 중복 방이 생긴다.
        verify(udmcService, never()).leaveChannel(USER_ID, CHANNEL_ID);
    }

    @Test
    void leaveChannel_그룹채널에_다른_참여자가_남아있으면_내_참여행만_삭제한다() {
        givenChannel(true);
        when(udmcService.countParticipants(CHANNEL_ID)).thenReturn(3L);

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        verify(udmcService).leaveChannel(USER_ID, CHANNEL_ID);
        verify(udmcService, never()).lockAndDeleteOneToOneMemberships(CHANNEL_ID);
        verifyNoInteractions(dmcJpaRepository);
    }

    @Test
    void leaveChannel_그룹채널에_한명만_남아도_채널을_유지한다() {
        givenChannel(true);
        when(udmcService.countParticipants(CHANNEL_ID)).thenReturn(2L);

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        verify(udmcService).leaveChannel(USER_ID, CHANNEL_ID);
        verifyNoInteractions(dmcJpaRepository);
    }

    @Test
    void leaveChannel_그룹채널의_마지막_한명이_나가면_채널도_삭제한다() {
        DirectMessageChannelEntity channel = givenChannel(true);
        when(udmcService.countParticipants(CHANNEL_ID)).thenReturn(1L);

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        verify(udmcService).leaveChannel(USER_ID, CHANNEL_ID);
        verify(dmcJpaRepository).delete(channel);
    }

    @Test
    void leaveChannel_그룹채널이면_참여행을_지우기_전에_참여자를_센다() {
        givenChannel(true);
        when(udmcService.countParticipants(CHANNEL_ID)).thenReturn(1L);

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        // 지운 뒤에 세면 "나가면 0명이 되는가" 를 알 수 없다.
        InOrder inOrder = inOrder(udmcService);
        inOrder.verify(udmcService).countParticipants(CHANNEL_ID);
        inOrder.verify(udmcService).leaveChannel(USER_ID, CHANNEL_ID);
    }

    @Test
    void leaveChannel_참여하지_않은_채널이면_NoSuchElementException을_전파한다() {
        when(udmcService.getUserChannel(USER_ID, CHANNEL_ID))
                .thenThrow(new NoSuchElementException("user direct message channel not found"));

        assertThatThrownBy(() -> directMessageService.leaveChannel(USER_ID, CHANNEL_ID))
                .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(dmcJpaRepository);
    }
}
