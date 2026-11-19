package com.danmalgi.backend.chat.client.mapper;

import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.dm.domain.model.UnreadCount;
import com.danmalgi.backend.dm.domain.model.UnreadCountQuery;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.ChannelLastMessage;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.ChannelUnreadCount;
import com.google.protobuf.Timestamp;

import java.time.Instant;

/**
 * chat-server 내부 proto ↔ 도메인 변환 static 매퍼.
 */
public class ChatMessageMapper {

    private ChatMessageMapper() {}

    public static LastMessage toDomainLastMessage(ChannelLastMessage proto) {
        return LastMessage.builder()
                .messageId(proto.getMessageId())
                .content(proto.getContent())
                .senderId(proto.getSenderId())
                .createdAt(proto.hasCreatedAt() ? toInstant(proto.getCreatedAt()) : null)
                .build();
    }

    public static UnreadCount toDomainUnreadCount(ChannelUnreadCount proto) {
        return new UnreadCount(proto.getCount(), proto.getTruncated());
    }

    // joined_at 은 선택 필드다. 모르는 값을 epoch 으로 채우면 "참여 시각 있음"으로 읽히므로 null 이면 비워 둔다.
    public static ChatStoreProto.UnreadCountQuery toProtoUnreadCountQuery(UnreadCountQuery query) {
        ChatStoreProto.UnreadCountQuery.Builder builder = ChatStoreProto.UnreadCountQuery.newBuilder()
                .setChannelId(query.channelId());
        if (query.joinedAt() != null) {
            builder.setJoinedAt(toTimestamp(query.joinedAt()));
        }
        return builder.build();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
