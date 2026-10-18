package com.danmalgi.backend.dm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;

import com.danmalgi.backend.chat.client.ChatMessageClient;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.global.infrastructure.image.ImageProcessor;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.service.UserDirectMessageChannelService;
import com.danmalgi.backend.user.domain.model.User;
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
    private static final Long OTHER_ID = 2L;
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

    private DirectMessageChannelEntity channel(boolean isGroup) {
        return DirectMessageChannelEntity.builder()
                .id(CHANNEL_ID)
                .isGroup(isGroup)
                .build();
    }

    private UserDirectMessageChannelEntity membership(DirectMessageChannelEntity channel, Long userId) {
        UserEntity user = UserEntity.from(
                new User(userId, userId + "@a.com", "user" + userId, "0000" + userId, null, 0));

        return UserDirectMessageChannelEntity.of(user, channel, "상대방");
    }

    @Test
    void leaveChannel_1대1채널이면_참여행_전체와_채널을_삭제한다() {
        DirectMessageChannelEntity channel = channel(false);
        List<UserDirectMessageChannelEntity> participants =
                List.of(membership(channel, USER_ID), membership(channel, OTHER_ID));
        when(udmcService.lockParticipants(CHANNEL_ID)).thenReturn(participants);

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        // 한쪽 행만 지우면 상대 목록에 참여자 1명짜리 방이 남고 재생성 시 중복 방이 생긴다.
        verify(udmcService).deleteMemberships(participants);
        verify(dmcJpaRepository).delete(channel);
    }

    @Test
    void leaveChannel_1대1채널이면_참여행을_지운_뒤_채널을_지운다() {
        DirectMessageChannelEntity channel = channel(false);
        when(udmcService.lockParticipants(CHANNEL_ID))
                .thenReturn(List.of(membership(channel, USER_ID), membership(channel, OTHER_ID)));

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        // UDMC 의 @ManyToOne 에 cascade/orphanRemoval 이 없어 DMC 를 먼저 지우면 FK 위반이다.
        InOrder inOrder = inOrder(udmcService, dmcJpaRepository);
        inOrder.verify(udmcService).deleteMemberships(anyList());
        inOrder.verify(dmcJpaRepository).delete(channel);
    }

    @Test
    void leaveChannel_그룹채널에_다른_참여자가_남아있으면_내_참여행만_삭제한다() {
        DirectMessageChannelEntity channel = channel(true);
        UserDirectMessageChannelEntity mine = membership(channel, USER_ID);
        when(udmcService.lockParticipants(CHANNEL_ID))
                .thenReturn(List.of(mine, membership(channel, OTHER_ID), membership(channel, 3L)));

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        verify(udmcService).deleteMemberships(List.of(mine));
        verifyNoInteractions(dmcJpaRepository);
    }

    @Test
    void leaveChannel_그룹채널에_한명만_남아도_채널을_유지한다() {
        DirectMessageChannelEntity channel = channel(true);
        UserDirectMessageChannelEntity mine = membership(channel, USER_ID);
        when(udmcService.lockParticipants(CHANNEL_ID))
                .thenReturn(List.of(mine, membership(channel, OTHER_ID)));

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        // 남은 한 명이 계속 봐야 하는 방이다.
        verify(udmcService).deleteMemberships(List.of(mine));
        verifyNoInteractions(dmcJpaRepository);
    }

    @Test
    void leaveChannel_그룹채널의_마지막_한명이_나가면_채널도_삭제한다() {
        DirectMessageChannelEntity channel = channel(true);
        UserDirectMessageChannelEntity mine = membership(channel, USER_ID);
        when(udmcService.lockParticipants(CHANNEL_ID)).thenReturn(List.of(mine));

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        verify(udmcService).deleteMemberships(List.of(mine));
        verify(dmcJpaRepository).delete(channel);
    }

    /**
     * 예전에는 참여자 수를 따로 COUNT 해서 판정했다. 2인 그룹에서 둘이 동시에 나가면 양쪽 다 2를
     * 읽어 아무도 방을 지우지 않고 고아 채널이 남았다. 이제는 잠근 뒤 읽은 목록으로만 판정하므로
     * 뒤에 들어온 쪽이 1건짜리 목록을 받아 방을 지운다.
     */
    @Test
    void leaveChannel_참여자수를_잠금조회_목록으로만_판정한다() {
        DirectMessageChannelEntity channel = channel(true);
        when(udmcService.lockParticipants(CHANNEL_ID))
                .thenReturn(List.of(membership(channel, USER_ID)));

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        verify(dmcJpaRepository).delete(channel);
    }

    @Test
    void leaveChannel_판정보다_잠금조회를_먼저_한다() {
        DirectMessageChannelEntity channel = channel(true);
        UserDirectMessageChannelEntity mine = membership(channel, USER_ID);
        when(udmcService.lockParticipants(CHANNEL_ID)).thenReturn(List.of(mine));

        directMessageService.leaveChannel(USER_ID, CHANNEL_ID);

        // 앞에 락 없는 조회를 두면 그 결과로 판단하게 되어 직렬화가 의미를 잃는다.
        InOrder inOrder = inOrder(udmcService);
        inOrder.verify(udmcService).lockParticipants(CHANNEL_ID);
        inOrder.verify(udmcService).deleteMemberships(anyList());
        verify(udmcService, never()).getUserChannel(USER_ID, CHANNEL_ID);
    }

    @Test
    void leaveChannel_참여하지_않은_채널이면_NoSuchElementException() {
        DirectMessageChannelEntity channel = channel(true);
        when(udmcService.lockParticipants(CHANNEL_ID))
                .thenReturn(List.of(membership(channel, OTHER_ID)));

        assertThatThrownBy(() -> directMessageService.leaveChannel(USER_ID, CHANNEL_ID))
                .isInstanceOf(NoSuchElementException.class);

        verify(udmcService, never()).deleteMemberships(anyList());
        verifyNoInteractions(dmcJpaRepository);
    }

    @Test
    void leaveChannel_없는_채널이면_NoSuchElementException() {
        when(udmcService.lockParticipants(CHANNEL_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> directMessageService.leaveChannel(USER_ID, CHANNEL_ID))
                .isInstanceOf(NoSuchElementException.class);

        verify(udmcService, never()).deleteMemberships(anyList());
        verifyNoInteractions(dmcJpaRepository);
    }

    /**
     * GlobalGrpcExceptionHandler 가 예외 메시지를 NOT_FOUND description 으로 그대로 내보낸다.
     * 문구가 갈리면 "없는 방" 과 "남의 방" 을 구분하는 오라클이 되어 방의 존재가 드러난다.
     */
    @Test
    void leaveChannel_없는_채널과_미참여_채널의_예외_메시지가_같다() {
        when(udmcService.lockParticipants(CHANNEL_ID)).thenReturn(List.of());
        String absent = catchMessage();

        DirectMessageChannelEntity channel = channel(true);
        when(udmcService.lockParticipants(CHANNEL_ID))
                .thenReturn(List.of(membership(channel, OTHER_ID)));
        String notParticipating = catchMessage();

        assertThat(absent).isEqualTo(notParticipating);
    }

    private String catchMessage() {
        try {
            directMessageService.leaveChannel(USER_ID, CHANNEL_ID);
            throw new AssertionError("NoSuchElementException 이 나야 한다");
        } catch (NoSuchElementException e) {
            return e.getMessage();
        }
    }
}
