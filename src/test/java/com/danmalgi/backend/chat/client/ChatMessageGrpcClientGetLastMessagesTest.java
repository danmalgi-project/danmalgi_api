package com.danmalgi.backend.chat.client;

import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.ChannelLastMessage;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.GetLastMessagesResponse;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreServiceGrpc.ChatStoreServiceBlockingStub;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageGrpcClientGetLastMessagesTest {

    private ChatStoreServiceBlockingStub stub;
    private ChatMessageGrpcClient client;

    @BeforeEach
    void setUp() {
        stub = mock(ChatStoreServiceBlockingStub.class);
        client = new ChatMessageGrpcClient(stub);
    }

    @Test
    void getLastMessages_여러_channelId를_단일_배치로_1회만_호출하고_Map으로_변환() {
        List<Long> channelIds = List.of(1L, 2L, 3L);
        GetLastMessagesResponse response = GetLastMessagesResponse.newBuilder()
                .addLastMessages(ChannelLastMessage.newBuilder()
                        .setChannelId(1L).setMessageId(11L).setContent("첫번째").setSenderId(100L).build())
                .addLastMessages(ChannelLastMessage.newBuilder()
                        .setChannelId(3L).setMessageId(33L).setContent("세번째").setSenderId(300L).build())
                .build();
        when(stub.getLastMessages(any())).thenReturn(response);

        Map<Long, LastMessage> result = client.getLastMessages(channelIds);

        // 핵심: 채널당 1콜이 아니라 전체를 단일 요청으로 정확히 1회 호출
        verify(stub, times(1)).getLastMessages(argThat(req -> req.getChannelIdsList().equals(channelIds)));

        assertThat(result).containsOnlyKeys(1L, 3L);
        assertThat(result.get(1L).getMessageId()).isEqualTo(11L);
        assertThat(result.get(1L).getContent()).isEqualTo("첫번째");
        assertThat(result.get(3L).getSenderId()).isEqualTo(300L);
    }

    @Test
    void getLastMessages_빈_channelIds면_stub_미호출하고_빈_Map_반환() {
        Map<Long, LastMessage> result = client.getLastMessages(List.of());

        verify(stub, never()).getLastMessages(any());
        assertThat(result).isEmpty();
    }

    @Test
    void getLastMessages_stub이_StatusRuntimeException을_던지면_빈_Map으로_graceful_degrade() {
        when(stub.getLastMessages(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        Map<Long, LastMessage> result = client.getLastMessages(List.of(1L, 2L));

        assertThat(result).isEmpty();
    }
}
