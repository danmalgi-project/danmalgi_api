package com.danmalgi.backend.dm.grpc.mapper;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectMessageMapperToProtoDirectMessageChannelListItemTest {

    @Test
    void toProtoDirectMessageChannelListItem_channel이_채워진다() {
        DirectMessage directMessage = new DirectMessage(1L, "Bob", false, null, null, null);

        DirectMessageProto.DirectMessageChannelListItem result =
                DirectMessageMapper.toProtoDirectMessageChannelListItem(directMessage);

        assertThat(result.hasChannel()).isTrue();
        assertThat(result.getChannel().getDmId()).isEqualTo(1L);
        assertThat(result.getChannel().getChannelName()).isEqualTo("Bob");
    }

    @Test
    void toProtoDirectMessageChannelListItem_lastMessage가_있으면_설정() {
        LastMessage lastMessage = LastMessage.builder()
                .messageId(10L)
                .content("hello")
                .senderId(7L)
                .createdAt(Instant.ofEpochSecond(1_700_000_000L))
                .build();
        DirectMessage directMessage = new DirectMessage(1L, "Bob", false, null, null, lastMessage);

        DirectMessageProto.DirectMessageChannelListItem result =
                DirectMessageMapper.toProtoDirectMessageChannelListItem(directMessage);

        assertThat(result.hasLastMessage()).isTrue();
        assertThat(result.getLastMessage().getMessageId()).isEqualTo(10L);
        assertThat(result.getLastMessage().getContent()).isEqualTo("hello");
        assertThat(result.getLastMessage().getSenderId()).isEqualTo(7L);
    }

    @Test
    void toProtoDirectMessageChannelListItem_lastMessage가_null이면_unset() {
        DirectMessage directMessage = new DirectMessage(1L, "Bob", false, null, null, null);

        DirectMessageProto.DirectMessageChannelListItem result =
                DirectMessageMapper.toProtoDirectMessageChannelListItem(directMessage);

        assertThat(result.hasLastMessage()).isFalse();
    }

    @Test
    void toProtoDirectMessageChannelListItems_빈_목록이면_빈_목록_반환() {
        List<DirectMessageProto.DirectMessageChannelListItem> result =
                DirectMessageMapper.toProtoDirectMessageChannelListItems(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void toProtoDirectMessageChannelListItems_null이면_빈_목록_반환() {
        List<DirectMessageProto.DirectMessageChannelListItem> result =
                DirectMessageMapper.toProtoDirectMessageChannelListItems(null);

        assertThat(result).isEmpty();
    }

    @Test
    void toProtoDirectMessageChannelListItems_목록_변환() {
        List<DirectMessage> messages = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null)
        );

        List<DirectMessageProto.DirectMessageChannelListItem> result =
                DirectMessageMapper.toProtoDirectMessageChannelListItems(messages);

        assertThat(result).hasSize(2);
    }
}
