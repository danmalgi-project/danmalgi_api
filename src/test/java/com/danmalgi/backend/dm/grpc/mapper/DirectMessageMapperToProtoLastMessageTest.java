package com.danmalgi.backend.dm.grpc.mapper;

import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DirectMessageMapperToProtoLastMessageTest {

    @Test
    void toProtoLastMessage_정상_변환() {
        Instant createdAt = Instant.ofEpochSecond(1_700_000_000L, 123_000_000);
        LastMessage lastMessage = LastMessage.builder()
                .messageId(10L)
                .content("hello")
                .senderId(7L)
                .createdAt(createdAt)
                .build();

        DirectMessageProto.LastMessage result =
                DirectMessageMapper.toProtoLastMessage(lastMessage);

        assertThat(result.getMessageId()).isEqualTo(10L);
        assertThat(result.getContent()).isEqualTo("hello");
        assertThat(result.getSenderId()).isEqualTo(7L);
        assertThat(result.getCreatedAt().getSeconds()).isEqualTo(1_700_000_000L);
        assertThat(result.getCreatedAt().getNanos()).isEqualTo(123_000_000);
    }

    @Test
    void toProtoLastMessage_createdAt이_null이면_created_at_unset() {
        LastMessage lastMessage = LastMessage.builder()
                .messageId(10L)
                .content("hello")
                .senderId(7L)
                .createdAt(null)
                .build();

        DirectMessageProto.LastMessage result =
                DirectMessageMapper.toProtoLastMessage(lastMessage);

        assertThat(result.hasCreatedAt()).isFalse();
    }

    @Test
    void toProtoLastMessage_messageId가_null이면_0으로_처리() {
        LastMessage lastMessage = LastMessage.builder()
                .messageId(null)
                .content("hello")
                .senderId(7L)
                .createdAt(null)
                .build();

        DirectMessageProto.LastMessage result =
                DirectMessageMapper.toProtoLastMessage(lastMessage);

        assertThat(result.getMessageId()).isEqualTo(0L);
    }

    @Test
    void toProtoLastMessage_content가_null이면_빈_문자열로_처리() {
        LastMessage lastMessage = LastMessage.builder()
                .messageId(10L)
                .content(null)
                .senderId(7L)
                .createdAt(null)
                .build();

        DirectMessageProto.LastMessage result =
                DirectMessageMapper.toProtoLastMessage(lastMessage);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void toProtoLastMessage_senderId가_null이면_0으로_처리() {
        LastMessage lastMessage = LastMessage.builder()
                .messageId(10L)
                .content("hello")
                .senderId(null)
                .createdAt(null)
                .build();

        DirectMessageProto.LastMessage result =
                DirectMessageMapper.toProtoLastMessage(lastMessage);

        assertThat(result.getSenderId()).isEqualTo(0L);
    }
}
