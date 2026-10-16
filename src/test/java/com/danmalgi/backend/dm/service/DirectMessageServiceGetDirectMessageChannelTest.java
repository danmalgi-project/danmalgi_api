package com.danmalgi.backend.dm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.danmalgi.backend.chat.client.ChatMessageClient;
import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.global.infrastructure.image.ImageProcessor;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.udmc.service.UserDirectMessageChannelService;
import com.danmalgi.backend.user.domain.model.User;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceGetDirectMessageChannelTest {

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
    void getDirectMessageChannel_참여중인_방이면_URL을_적용한_채널을_반환() {
        Long userId = 1L;
        Long channelId = 10L;
        User friend = new User(2L, "b@b.com", "Bob", "00002", null, 0);
        friend.setProfileImageUrl("profiles/2/bob.webp");

        when(udmcService.getDirectMessage(userId, channelId))
                .thenReturn(new DirectMessage(channelId, "Study Room", true, "channels/10/abc.webp", List.of(friend), null));
        when(r2Uploader.generatePresignedUrl("channels/10/abc.webp"))
                .thenReturn("https://signed/channels/10/abc.webp");
        when(r2Uploader.toPublicUrl("profiles/2/bob.webp"))
                .thenReturn("https://public/profiles/2/bob.webp");

        DirectMessage result = directMessageService.getDirectMessageChannel(userId, channelId);

        assertThat(result.getId()).isEqualTo(channelId);
        assertThat(result.getChannelName()).isEqualTo("Study Room");
        assertThat(result.getChannelImageUrl()).isEqualTo("https://signed/channels/10/abc.webp");
        assertThat(result.getUsers())
                .extracting(User::getProfileImageUrl)
                .containsExactly("https://public/profiles/2/bob.webp");
        assertThat(result.getLastMessage()).isNull();
        verifyNoInteractions(chatMessageClient);
    }

    @Test
    void getDirectMessageChannel_채널이미지가_없으면_presignedUrl을_만들지_않음() {
        Long userId = 1L;
        Long channelId = 10L;

        when(udmcService.getDirectMessage(userId, channelId))
                .thenReturn(new DirectMessage(channelId, "Bob", false, null, List.of(), null));

        DirectMessage result = directMessageService.getDirectMessageChannel(userId, channelId);

        assertThat(result.getChannelImageUrl()).isNull();
        verify(r2Uploader, never()).generatePresignedUrl(any());
        verifyNoInteractions(chatMessageClient);
    }

    @Test
    void getDirectMessageChannel_참여하지_않았거나_없는_방이면_NoSuchElementException() {
        Long userId = 1L;
        Long channelId = 10L;

        when(udmcService.getDirectMessage(userId, channelId))
                .thenThrow(new NoSuchElementException("missing membership"));

        assertThatThrownBy(() -> directMessageService.getDirectMessageChannel(userId, channelId))
                .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(r2Uploader, chatMessageClient);
    }
}
