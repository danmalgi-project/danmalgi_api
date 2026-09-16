package com.danmalgi.backend.dm.service;

import com.danmalgi.backend.chat.client.ChatMessageClient;
import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.global.infrastructure.image.ImageProcessor;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.udmc.service.UserDirectMessageChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceCreateChannelTest {

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

    @Test
    void createChannel_1대1_채널은_isGroup이_false() {
        DirectMessageChannelEntity savedEntity = DirectMessageChannelEntity.builder()
                .id(10L)
                .isGroup(false)
                .build();

        when(udmcService.lockAndFindOneToOneChannelId(List.of(1L, 2L)))
                .thenReturn(Optional.empty());
        when(dmcJpaRepository.save(any())).thenReturn(savedEntity);
        when(udmcService.createUserChannel(1L, List.of(2L), 10L))
                .thenReturn(new DirectMessage(10L, "Bob", false, null, null, null));

        DirectMessage result = directMessageService.createChannel(1L, List.of(2L));

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getChannelName()).isEqualTo("Bob");
        assertThat(result.getIsGroup()).isFalse();
        verify(dmcJpaRepository).save(argThat(entity -> !entity.getIsGroup()));
    }

    @Test
    void createChannel_기존_1대1_채널이_있으면_새_채널을_만들지_않고_기존_dm을_반환한다() {
        when(udmcService.lockAndFindOneToOneChannelId(List.of(1L, 2L)))
                .thenReturn(Optional.of(10L));
        when(udmcService.getDirectMessage(1L, 10L))
                .thenReturn(new DirectMessage(10L, "Bob", false, null, null, null));

        DirectMessage result = directMessageService.createChannel(1L, List.of(2L));

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getChannelName()).isEqualTo("Bob");
        verify(dmcJpaRepository, never()).save(any());
        verify(udmcService, never()).createUserChannel(anyLong(), anyList(), anyLong());
    }

    @Test
    void createChannel_그룹_요청은_기존_채널을_조회하지_않고_매번_새로_생성한다() {
        DirectMessageChannelEntity savedEntity = DirectMessageChannelEntity.builder()
                .id(13L)
                .isGroup(true)
                .build();

        when(dmcJpaRepository.save(any())).thenReturn(savedEntity);
        when(udmcService.createUserChannel(1L, List.of(2L, 3L), 13L))
                .thenReturn(new DirectMessage(13L, "Bob,Carol", true, null, null, null));

        directMessageService.createChannel(1L, List.of(2L, 3L));

        verify(udmcService, never()).lockAndFindOneToOneChannelId(anyList());
    }

    @Test
    void createChannel_그룹_채널은_isGroup이_true() {
        DirectMessageChannelEntity savedEntity = DirectMessageChannelEntity.builder()
                .id(11L)
                .isGroup(true)
                .build();

        when(dmcJpaRepository.save(any())).thenReturn(savedEntity);
        when(udmcService.createUserChannel(1L, List.of(2L, 3L), 11L))
                .thenReturn(new DirectMessage(11L, "Bob,Carol", true, null, null, null));

        DirectMessage result = directMessageService.createChannel(1L, List.of(2L, 3L));

        assertThat(result.getChannelName()).isEqualTo("Bob,Carol");
        assertThat(result.getIsGroup()).isTrue();
        verify(dmcJpaRepository).save(argThat(DirectMessageChannelEntity::getIsGroup));
    }

    @Test
    void createChannel_channelImageUrl이_path이면_presigned_URL로_치환된다() {
        DirectMessageChannelEntity savedEntity = DirectMessageChannelEntity.builder()
                .id(12L)
                .isGroup(true)
                .build();

        when(dmcJpaRepository.save(any())).thenReturn(savedEntity);
        when(udmcService.createUserChannel(1L, List.of(2L, 3L), 12L))
                .thenReturn(new DirectMessage(12L, "Group", true, "channels/12/img.webp", new java.util.ArrayList<>(), null));
        when(r2Uploader.generatePresignedUrl("channels/12/img.webp"))
                .thenReturn("https://signed/channels/12/img.webp");

        DirectMessage result = directMessageService.createChannel(1L, List.of(2L, 3L));

        assertThat(result.getChannelImageUrl())
                .isEqualTo("https://signed/channels/12/img.webp");
        verify(r2Uploader).generatePresignedUrl("channels/12/img.webp");
    }
}
