package com.danmalgi.backend.dm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danmalgi.backend.chat.client.ChatMessageClient;
import com.danmalgi.backend.dm.domain.exception.NotGroupChannelException;
import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.global.infrastructure.image.ImageProcessor;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.service.UserDirectMessageChannelService;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceUpdateChannelTest {

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
    void updateChannel_updatesGroupNameAndImageAndReturnsSnapshot() {
        Long userId = 1L;
        Long channelId = 10L;
        byte[] originalImage = "png-bytes".getBytes();
        byte[] webpImage = "webp-bytes".getBytes();
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder()
                .id(channelId)
                .isGroup(true)
                .build();
        UserDirectMessageChannelEntity membership = mock(UserDirectMessageChannelEntity.class);

        when(membership.getDirectMessageChannel()).thenReturn(channel);
        when(udmcService.getUserChannel(userId, channelId)).thenReturn(membership);
        when(imageProcessor.toWebp(originalImage)).thenReturn(webpImage);
        when(r2Uploader.uploadChannelImage(channelId, webpImage, "webp"))
                .thenReturn("channels/10/new.webp");
        when(udmcService.getDirectMessage(userId, channelId))
                .thenReturn(new DirectMessage(channelId, "Study Room", true, "channels/10/new.webp", List.of(), null));
        when(r2Uploader.generatePresignedUrl("channels/10/new.webp"))
                .thenReturn("https://signed/channels/10/new.webp");

        DirectMessage result = directMessageService.updateChannel(userId, channelId, "Study Room", originalImage, "png");

        assertThat(result.getChannelName()).isEqualTo("Study Room");
        assertThat(result.getChannelImageUrl()).isEqualTo("https://signed/channels/10/new.webp");
        verify(udmcService).updateChannelName(channelId, "Study Room");
        verify(imageProcessor).toWebp(originalImage);
        verify(r2Uploader).uploadChannelImage(channelId, webpImage, "webp");
        verify(dmcJpaRepository).save(channel);
        verify(r2Uploader).generatePresignedUrl("channels/10/new.webp");
    }

    @Test
    void updateChannel_rejectsOneToOneChannel() {
        Long userId = 1L;
        Long channelId = 10L;
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder()
                .id(channelId)
                .isGroup(false)
                .build();
        UserDirectMessageChannelEntity membership = mock(UserDirectMessageChannelEntity.class);

        when(membership.getDirectMessageChannel()).thenReturn(channel);
        when(udmcService.getUserChannel(userId, channelId)).thenReturn(membership);

        assertThatThrownBy(() ->
                directMessageService.updateChannel(userId, channelId, "Study Room", null, ""))
                .isInstanceOf(NotGroupChannelException.class);

        verify(udmcService, never()).updateChannelName(any(), any());
        verify(dmcJpaRepository, never()).save(any());
    }

    @Test
    void updateChannel_propagatesMissingMembership() {
        Long userId = 1L;
        Long channelId = 10L;

        when(udmcService.getUserChannel(userId, channelId))
                .thenThrow(new NoSuchElementException("missing membership"));

        assertThatThrownBy(() ->
                directMessageService.updateChannel(userId, channelId, "Study Room", null, ""))
                .isInstanceOf(NoSuchElementException.class);

        verify(udmcService, never()).updateChannelName(eq(channelId), any());
    }
}
