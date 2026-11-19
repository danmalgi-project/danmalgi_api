package com.danmalgi.backend.dm.service;

import com.danmalgi.backend.chat.client.ChatMessageClient;
import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.dm.domain.model.UnreadCount;
import com.danmalgi.backend.dm.domain.model.UnreadCountQuery;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
    void getDirectMessageChannels_각_채널에_마지막메시지가_조합되고_없는채널은_null() {
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null),
                new DirectMessage(3L, "Dave", false, null, null, null)
        );
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(channels);

        LastMessage lastMessage1 = LastMessage.builder()
                .messageId(10L).content("안녕").senderId(1L).createdAt(Instant.parse("2026-09-26T00:10:00Z")).build();
        LastMessage lastMessage3 = LastMessage.builder()
                .messageId(30L).content("반가워").senderId(3L).createdAt(Instant.parse("2026-09-26T00:20:00Z")).build();
        // 채널 2는 마지막 메시지가 없음(맵에 키 없음)
        when(chatMessageClient.getLastMessages(anyList()))
                .thenReturn(Map.of(1L, lastMessage1, 3L, lastMessage3));

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        // 정렬(최신순)과 무관하게, 각 채널에 마지막 메시지가 올바르게 조합됐는지만 본다.
        // 순서 자체의 기대는 아래 정렬 전용 테스트들이 담당한다.
        assertThat(result).hasSize(3);
        assertThat(result).filteredOn(dm -> dm.getId().equals(1L))
                .extracting(DirectMessage::getLastMessage).containsExactly(lastMessage1);
        assertThat(result).filteredOn(dm -> dm.getId().equals(2L))
                .extracting(DirectMessage::getLastMessage).containsExactly((LastMessage) null);
        assertThat(result).filteredOn(dm -> dm.getId().equals(3L))
                .extracting(DirectMessage::getLastMessage).containsExactly(lastMessage3);
    }

    @Test
    void getDirectMessageChannels_마지막메시지가있는채널들_최신순으로정렬된다() {
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null),
                new DirectMessage(3L, "Dave", false, null, null, null)
        );
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(channels);

        // 1 -> 10분 전, 2 -> 1분 전(가장 최신), 3 -> 5분 전
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of(
                1L, LastMessage.builder().messageId(10L).createdAt(Instant.parse("2026-09-26T00:00:00Z")).build(),
                2L, LastMessage.builder().messageId(20L).createdAt(Instant.parse("2026-09-26T00:09:00Z")).build(),
                3L, LastMessage.builder().messageId(30L).createdAt(Instant.parse("2026-09-26T00:05:00Z")).build()
        ));

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        assertThat(result).extracting(DirectMessage::getId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void getDirectMessageChannels_마지막메시지가없는채널_맨뒤로간다() {
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null),
                new DirectMessage(3L, "Dave", false, null, null, null),
                new DirectMessage(4L, "Eve", false, null, null, null)
        );
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(channels);

        // 1, 3 만 마지막 메시지가 있음. 2, 4 는 맵에 키 없음(메시지 0건)
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of(
                1L, LastMessage.builder().messageId(10L).createdAt(Instant.parse("2026-09-26T00:00:00Z")).build(),
                3L, LastMessage.builder().messageId(30L).createdAt(Instant.parse("2026-09-26T00:05:00Z")).build()
        ));

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        // 메시지 있는 채널(3, 1)이 먼저 최신순, 메시지 없는 채널(4, 2)이 그 뒤에 id 내림차순
        assertThat(result).extracting(DirectMessage::getId).containsExactly(3L, 1L, 4L, 2L);
    }

    @Test
    void getDirectMessageChannels_마지막메시지시간이같은채널_dmId내림차순으로정렬된다() {
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null),
                new DirectMessage(3L, "Dave", false, null, null, null)
        );
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(channels);

        Instant sameInstant = Instant.parse("2026-09-26T00:00:00Z");
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of(
                1L, LastMessage.builder().messageId(10L).createdAt(sameInstant).build(),
                2L, LastMessage.builder().messageId(20L).createdAt(sameInstant).build(),
                3L, LastMessage.builder().messageId(30L).createdAt(sameInstant).build()
        ));

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        assertThat(result).extracting(DirectMessage::getId).containsExactly(3L, 2L, 1L);
    }

    @Test
    void getDirectMessageChannels_마지막메시지에_시간이없으면_맨뒤로간다() {
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null)
        );
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(channels);

        // 채널 1은 lastMessage 는 있지만 createdAt 이 없는 방어적 예외 케이스(chat-server 가 안 준 경우).
        // 메시지 없는 채널(2, 맵에 키 없음)과 같은 그룹으로 취급해 맨 뒤로 보낸다.
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of(
                1L, LastMessage.builder().messageId(10L).createdAt(null).build()
        ));

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        // 둘 다 "시간 없음" 그룹이라 dmId 내림차순
        assertThat(result).extracting(DirectMessage::getId).containsExactly(2L, 1L);
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
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UnreadCountQuery>> unreadCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMessageClient, times(1)).getUnreadCounts(eq(1L), unreadCaptor.capture());
        assertThat(unreadCaptor.getValue()).extracting(UnreadCountQuery::channelId).containsExactly(1L, 2L, 3L);
        // 채널당 추가 호출(N콜) 없음
        verifyNoMoreInteractions(chatMessageClient);
    }

    @Test
    void getDirectMessageChannels_각_채널에_안읽은수가_조합되고_없는채널은_0() {
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null),
                new DirectMessage(3L, "Dave", false, null, null, null)
        );
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(channels);
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of());
        // 채널 2는 chat-server 조회 실패로 응답에서 빠진 경우(맵에 키 없음)
        when(chatMessageClient.getUnreadCounts(eq(1L), anyList())).thenReturn(Map.of(
                1L, new UnreadCount(3, false),
                3L, new UnreadCount(99, true)
        ));

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        assertThat(result).filteredOn(dm -> dm.getId().equals(1L))
                .extracting(DirectMessage::getUnreadCount, DirectMessage::isUnreadCountTruncated)
                .containsExactly(tuple(3, false));
        assertThat(result).filteredOn(dm -> dm.getId().equals(2L))
                .extracting(DirectMessage::getUnreadCount, DirectMessage::isUnreadCountTruncated)
                .containsExactly(tuple(0, false));
        assertThat(result).filteredOn(dm -> dm.getId().equals(3L))
                .extracting(DirectMessage::getUnreadCount, DirectMessage::isUnreadCountTruncated)
                .containsExactly(tuple(99, true));
    }

    @Test
    void getDirectMessageChannels_채널별_joinedAt을_안읽은수_조회에_그대로_넘긴다() {
        DirectMessage withJoinedAt = new DirectMessage(1L, "Group", true, null, null, null);
        Instant joinedAt = Instant.parse("2026-11-01T09:00:00Z");
        withJoinedAt.setJoinedAt(joinedAt);
        // joined_at 컬럼 추가 전 참여 행 — null 그대로 넘겨 client 가 생략하게 한다
        DirectMessage withoutJoinedAt = new DirectMessage(2L, "Bob", false, null, null, null);
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(List.of(withJoinedAt, withoutJoinedAt));
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of());

        directMessageService.getDirectMessageChannels(1L);

        verify(chatMessageClient).getUnreadCounts(1L, List.of(
                new UnreadCountQuery(1L, joinedAt),
                new UnreadCountQuery(2L, null)
        ));
    }

    @Test
    void getDirectMessageChannels_안읽은수만_degrade돼도_마지막메시지와_정렬은_유지된다() {
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null)
        );
        when(udmcService.getDirectMessagesByUserId(1L)).thenReturn(channels);
        LastMessage lastMessage1 = LastMessage.builder()
                .messageId(10L).createdAt(Instant.parse("2026-09-26T00:10:00Z")).build();
        when(chatMessageClient.getLastMessages(anyList())).thenReturn(Map.of(1L, lastMessage1));
        // client 가 실패를 빈 맵으로 degrade 한 상황
        when(chatMessageClient.getUnreadCounts(eq(1L), anyList())).thenReturn(Map.of());

        List<DirectMessage> result = directMessageService.getDirectMessageChannels(1L);

        assertThat(result).extracting(DirectMessage::getId).containsExactly(1L, 2L);
        assertThat(result.get(0).getLastMessage()).isEqualTo(lastMessage1);
        assertThat(result).extracting(DirectMessage::getUnreadCount).containsOnly(0);
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
        User user = new User(2L, "test@test.com", "Bob", "00002", null, UserStatus.ACTIVE.getNumber());
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
