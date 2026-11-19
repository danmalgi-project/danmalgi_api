package com.danmalgi.backend.chat.client.mapper;

import com.danmalgi.backend.dm.domain.model.UnreadCountQuery;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageMapperToProtoUnreadCountQueryTest {

    @Test
    void toProtoUnreadCountQuery_joinedAt을_Timestamp로_변환() {
        Instant joinedAt = Instant.parse("2026-11-01T09:00:00.500Z");

        ChatStoreProto.UnreadCountQuery result =
                ChatMessageMapper.toProtoUnreadCountQuery(new UnreadCountQuery(10L, joinedAt));

        assertThat(result.getChannelId()).isEqualTo(10L);
        assertThat(result.hasJoinedAt()).isTrue();
        assertThat(result.getJoinedAt().getSeconds()).isEqualTo(joinedAt.getEpochSecond());
        assertThat(result.getJoinedAt().getNanos()).isEqualTo(joinedAt.getNano());
    }

    @Test
    void toProtoUnreadCountQuery_joinedAt이_null이면_필드를_비운다() {
        ChatStoreProto.UnreadCountQuery result =
                ChatMessageMapper.toProtoUnreadCountQuery(new UnreadCountQuery(10L, null));

        assertThat(result.getChannelId()).isEqualTo(10L);
        assertThat(result.hasJoinedAt()).isFalse();
    }
}
