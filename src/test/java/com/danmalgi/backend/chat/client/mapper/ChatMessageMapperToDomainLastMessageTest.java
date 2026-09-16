package com.danmalgi.backend.chat.client.mapper;

import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.ChannelLastMessage;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageMapperToDomainLastMessageTest {

    @Test
    void toDomainLastMessage_모든_필드_매핑_후_Timestamp를_Instant로_변환() {
        Instant createdAt = Instant.parse("2026-07-19T10:15:30.500Z");
        ChannelLastMessage proto = ChannelLastMessage.newBuilder()
                .setChannelId(10L)
                .setMessageId(100L)
                .setContent("안녕하세요")
                .setSenderId(7L)
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(createdAt.getEpochSecond())
                        .setNanos(createdAt.getNano())
                        .build())
                .build();

        LastMessage result = ChatMessageMapper.toDomainLastMessage(proto);

        assertThat(result.getMessageId()).isEqualTo(100L);
        assertThat(result.getContent()).isEqualTo("안녕하세요");
        assertThat(result.getSenderId()).isEqualTo(7L);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void toDomainLastMessage_createdAt_미설정시_null_처리() {
        ChannelLastMessage proto = ChannelLastMessage.newBuilder()
                .setChannelId(10L)
                .setMessageId(100L)
                .setContent("hi")
                .setSenderId(7L)
                .build(); // created_at unset

        LastMessage result = ChatMessageMapper.toDomainLastMessage(proto);

        assertThat(result.getMessageId()).isEqualTo(100L);
        assertThat(result.getContent()).isEqualTo("hi");
        assertThat(result.getSenderId()).isEqualTo(7L);
        assertThat(result.getCreatedAt()).isNull();
    }
}
