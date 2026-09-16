package com.danmalgi.backend.chat.client.mapper;

import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.ChannelLastMessage;
import com.google.protobuf.Timestamp;

import java.time.Instant;

/**
 * chat-server 내부 proto(ChannelLastMessage) → 도메인(LastMessage) 변환 static 매퍼.
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

    private static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
