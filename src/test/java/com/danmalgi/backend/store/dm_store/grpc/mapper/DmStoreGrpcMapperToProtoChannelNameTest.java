package com.danmalgi.backend.store.dm_store.grpc.mapper;

import com.danmalgi.backend.internal.dm_store.v1.DmStoreProto.ChannelName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DmStoreGrpcMapperToProtoChannelNameTest {

    @Test
    void toProtoChannelName_userId와_channelName으로_ChannelName_프로토를_생성한다() {
        ChannelName result = DmStoreGrpcMapper.toProtoChannelName(10L, "Alice");

        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getChannelName()).isEqualTo("Alice");
    }
}
