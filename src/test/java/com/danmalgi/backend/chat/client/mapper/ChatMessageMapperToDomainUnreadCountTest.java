package com.danmalgi.backend.chat.client.mapper;

import com.danmalgi.backend.dm.domain.model.UnreadCount;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.ChannelUnreadCount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageMapperToDomainUnreadCountTest {

    @Test
    void toDomainUnreadCount_count와_truncated를_매핑() {
        ChannelUnreadCount proto = ChannelUnreadCount.newBuilder()
                .setChannelId(10L)
                .setCount(99)
                .setTruncated(true)
                .build();

        UnreadCount result = ChatMessageMapper.toDomainUnreadCount(proto);

        assertThat(result).isEqualTo(new UnreadCount(99, true));
    }

    @Test
    void toDomainUnreadCount_필드가_비어있으면_0과_false() {
        UnreadCount result = ChatMessageMapper.toDomainUnreadCount(
                ChannelUnreadCount.newBuilder().setChannelId(10L).build());

        assertThat(result).isEqualTo(new UnreadCount(0, false));
    }
}
