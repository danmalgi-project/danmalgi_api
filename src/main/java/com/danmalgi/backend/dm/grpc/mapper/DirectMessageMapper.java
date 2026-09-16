package com.danmalgi.backend.dm.grpc.mapper;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import com.danmalgi.backend.user.grpc.mapper.UserGrpcMapper;
import com.google.protobuf.Timestamp;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DirectMessageMapper {
    private DirectMessageMapper() {}

    public static List<DirectMessageProto.DirectMessageChannelListItem> toProtoDirectMessageChannelListItems(
            List<DirectMessage> directMessages
    ) {
        if (directMessages == null || directMessages.isEmpty()) {
            return Collections.emptyList();
        }

        return directMessages.stream()
                .map(DirectMessageMapper::toProtoDirectMessageChannelListItem)
                .collect(Collectors.toList());
    }

    // last_message 는 목록 응답 아이템에서만 제공된다.
    public static DirectMessageProto.DirectMessageChannelListItem toProtoDirectMessageChannelListItem(
            DirectMessage directMessage
    ) {
        DirectMessageProto.DirectMessageChannelListItem.Builder builder =
                DirectMessageProto.DirectMessageChannelListItem.newBuilder()
                        .setChannel(toProtoDirectMessageChannel(directMessage));

        if (directMessage.getLastMessage() != null) {
            builder.setLastMessage(toProtoLastMessage(directMessage.getLastMessage()));
        }

        return builder.build();
    }

    public static DirectMessageProto.DirectMessageChannel toProtoDirectMessageChannel(
            DirectMessage directMessage
    ) {
        if (directMessage == null) {
            throw new IllegalArgumentException("directMessage must not be null");
        }

        return DirectMessageProto.DirectMessageChannel.newBuilder()
                .setDmId(Optional.ofNullable(directMessage.getId()).orElse(0L))
                .setChannelName(Optional.ofNullable(directMessage.getChannelName()).orElse(""))
                .setIsGroup(Optional.ofNullable(directMessage.getIsGroup()).orElse(false))
                .setChannelImageUrl(Optional.ofNullable(directMessage.getChannelImageUrl()).orElse(""))
                .addAllUsers(Optional.ofNullable(directMessage.getUsers())
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(UserGrpcMapper::toProtoUser)
                    .toList())
                .build();
    }

    public static DirectMessageProto.LastMessage toProtoLastMessage(LastMessage lastMessage) {
        if (lastMessage == null) {
            throw new IllegalArgumentException("lastMessage must not be null");
        }

        DirectMessageProto.LastMessage.Builder builder = DirectMessageProto.LastMessage.newBuilder()
                .setMessageId(Optional.ofNullable(lastMessage.getMessageId()).orElse(0L))
                .setContent(Optional.ofNullable(lastMessage.getContent()).orElse(""))
                .setSenderId(Optional.ofNullable(lastMessage.getSenderId()).orElse(0L));

        Instant createdAt = lastMessage.getCreatedAt();
        if (createdAt != null) {
            builder.setCreatedAt(Timestamp.newBuilder()
                    .setSeconds(createdAt.getEpochSecond())
                    .setNanos(createdAt.getNano())
                    .build());
        }

        return builder.build();
    }
}
