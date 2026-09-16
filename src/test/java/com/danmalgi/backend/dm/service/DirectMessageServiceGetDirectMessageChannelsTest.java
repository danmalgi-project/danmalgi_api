package com.danmalgi.backend.dm.service;

import com.danmalgi.backend.chat.client.ChatMessageClient;
import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.global.infrastructure.image.ImageProcessor;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.udmc.service.UserDirectMessageChannelService;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceGetDirectMessageChannelsTest {

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
    void getDirectMessageChannels_각_채널에_마지막_메시지_조합_순서보존_없는채널은_null() {
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null),
                new DirectMessage(3L, "Dave", false, null, null, null)
        );
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(channels);

        LastMessage lastMessage1 = LastMessage.builder().messageId(10L).content("안녕").senderId(1L).build();
        LastMessage lastMessage3 = LastMessage.builder().messageId(30L).content("반가워").senderId(3L).build();
        // 채널 2는 마지막 메시지가 없음(맵에 키 없음)
        when(chatMessageClient.getLastMessages(anyList()))
                .thenReturn(Map.of(1L, lastMessage1, 3L, lastMessage3));

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        // 목록 순서 보존
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(2).getId()).isEqualTo(3L);

        // 각 채널에 마지막 메시지 세팅, 맵에 없는 채널은 null
        assertThat(result.get(0).getLastMessage()).isEqualTo(lastMessage1);
        assertThat(result.get(1).getLastMessage()).isNull();
        assertThat(result.get(2).getLastMessage()).isEqualTo(lastMessage3);
    }

    @Test
    void getDirectMessageChannels_채널_전체_id를_단일_배치로_1회만_호출() {
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null),
                new DirectMessage(3L, "Dave", false, null, null, null)
        );
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(channels);
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of());

        directMessageService.getDirectMessageChannels(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatMessageClient, times(1)).getLastMessages(captor.capture());
        // 채널 전체 id 를 목록 순서 그대로 단일 배치로 전달
        assertThat(captor.getValue()).containsExactly(1L, 2L, 3L);
        // 채널당 추가 호출(N콜) 없음
        verifyNoMoreInteractions(chatMessageClient);
    }

    @Test
    void getDirectMessageChannels_결과가_없으면_빈_목록_반환() {
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(List.of());
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of());

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getDirectMessageChannels_channelImageUrl이_path이면_presigned_URL로_치환된다() {
        List<DirectMessage> raw = new ArrayList<>(List.of(
                new DirectMessage(1L, "Group", true, "channels/1/img.webp", new ArrayList<>(), null)
        ));
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(raw);
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of());
        when(r2Uploader.generatePresignedUrl("channels/1/img.webp"))
                .thenReturn("https://signed/channels/1/img.webp");

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChannelImageUrl())
                .isEqualTo("https://signed/channels/1/img.webp");
        verify(r2Uploader).generatePresignedUrl("channels/1/img.webp");
    }

    @Test
    void getDirectMessageChannels_각_user의_profileImageUrl이_path이면_public_URL로_치환된다() {
        User user = new User(2L, "test@test.com", "Bob", "00002", null,
                "oauth-2", OauthType.KAKAO.getNumber(), UserStatus.ACTIVE.getNumber());
        user.setProfileImageUrl("profiles/2/img.webp");

        List<DirectMessage> raw = new ArrayList<>(List.of(
                new DirectMessage(1L, "Bob", false, null, new ArrayList<>(List.of(user)), null)
        ));
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(raw);
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of());
        when(r2Uploader.toPublicUrl("profiles/2/img.webp"))
                .thenReturn("https://cdn.example.com/profiles/2/img.webp");

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsers().get(0).getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/2/img.webp");
        verify(r2Uploader).toPublicUrl("profiles/2/img.webp");
    }
}
