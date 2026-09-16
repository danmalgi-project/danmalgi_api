package com.danmalgi.backend.store.dm_store.grpc.mapper;

import com.danmalgi.backend.internal.dm_store.v1.DmStoreProto.ChannelName;

public class DmStoreGrpcMapper {

    private DmStoreGrpcMapper() {}

    public static ChannelName toProtoChannelName(Long userId, String channelName) {
        return ChannelName.newBuilder()
                .setUserId(userId)
                .setChannelName(channelName)
                .build();
    }
}
