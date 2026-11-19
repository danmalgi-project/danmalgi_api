package com.danmalgi.backend.chat.client;

import com.danmalgi.backend.dm.domain.model.UnreadCount;
import com.danmalgi.backend.dm.domain.model.UnreadCountQuery;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.ChannelUnreadCount;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.GetUnreadCountsRequest;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.GetUnreadCountsResponse;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreServiceGrpc.ChatStoreServiceBlockingStub;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageGrpcClientGetUnreadCountsTest {

    private ChatStoreServiceBlockingStub stub;
    private ChatMessageGrpcClient client;

    @BeforeEach
    void setUp() {
        stub = mock(ChatStoreServiceBlockingStub.class);
        client = new ChatMessageGrpcClient(stub);
    }

    @Test
    void getUnreadCounts_userId와_전_채널을_단일_배치로_1회만_호출하고_Map으로_변환() {
        List<UnreadCountQuery> queries = List.of(
                new UnreadCountQuery(1L, null),
                new UnreadCountQuery(2L, null),
                new UnreadCountQuery(3L, null)
        );
        // 채널 2는 chat-server 조회 실패로 응답에서 빠진 경우
        GetUnreadCountsResponse response = GetUnreadCountsResponse.newBuilder()
                .addUnreadCounts(ChannelUnreadCount.newBuilder().setChannelId(1L).setCount(3).build())
                .addUnreadCounts(ChannelUnreadCount.newBuilder().setChannelId(3L).setCount(99).setTruncated(true).build())
                .build();
        when(stub.getUnreadCounts(any())).thenReturn(response);

        Map<Long, UnreadCount> result = client.getUnreadCounts(7L, queries);

        ArgumentCaptor<GetUnreadCountsRequest> captor = ArgumentCaptor.forClass(GetUnreadCountsRequest.class);
        verify(stub, times(1)).getUnreadCounts(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getChannelsList())
                .extracting(q -> q.getChannelId())
                .containsExactly(1L, 2L, 3L);

        assertThat(result).containsOnlyKeys(1L, 3L);
        assertThat(result.get(1L)).isEqualTo(new UnreadCount(3, false));
        assertThat(result.get(3L)).isEqualTo(new UnreadCount(99, true));
    }

    @Test
    void getUnreadCounts_joinedAt이_있으면_싣고_없으면_비운다() {
        Instant joinedAt = Instant.parse("2026-11-01T09:00:00.250Z");
        when(stub.getUnreadCounts(any())).thenReturn(GetUnreadCountsResponse.getDefaultInstance());

        client.getUnreadCounts(7L, List.of(
                new UnreadCountQuery(1L, joinedAt),
                new UnreadCountQuery(2L, null)
        ));

        ArgumentCaptor<GetUnreadCountsRequest> captor = ArgumentCaptor.forClass(GetUnreadCountsRequest.class);
        verify(stub).getUnreadCounts(captor.capture());
        assertThat(captor.getValue().getChannels(0).hasJoinedAt()).isTrue();
        assertThat(captor.getValue().getChannels(0).getJoinedAt().getSeconds()).isEqualTo(joinedAt.getEpochSecond());
        assertThat(captor.getValue().getChannels(0).getJoinedAt().getNanos()).isEqualTo(joinedAt.getNano());
        assertThat(captor.getValue().getChannels(1).hasJoinedAt()).isFalse();
    }

    @Test
    void getUnreadCounts_빈_queries면_stub_미호출하고_빈_Map_반환() {
        Map<Long, UnreadCount> result = client.getUnreadCounts(7L, List.of());

        verify(stub, never()).getUnreadCounts(any());
        assertThat(result).isEmpty();
    }

    // UNIMPLEMENTED: chat-server 가 아직 이 RPC 를 배포하지 않음. INVALID_ARGUMENT: 채널 500 개 초과.
    @ParameterizedTest
    @EnumSource(value = Status.Code.class, names = {"UNIMPLEMENTED", "UNAVAILABLE", "INVALID_ARGUMENT", "UNAUTHENTICATED"})
    void getUnreadCounts_stub이_StatusRuntimeException을_던지면_빈_Map으로_graceful_degrade(Status.Code code) {
        when(stub.getUnreadCounts(any())).thenThrow(new StatusRuntimeException(Status.fromCode(code)));

        Map<Long, UnreadCount> result = client.getUnreadCounts(7L, List.of(new UnreadCountQuery(1L, null)));

        assertThat(result).isEmpty();
    }
}
