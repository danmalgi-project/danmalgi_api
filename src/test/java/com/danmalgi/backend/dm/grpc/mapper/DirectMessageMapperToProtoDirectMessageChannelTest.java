package com.danmalgi.backend.dm.grpc.mapper;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectMessageMapperToProtoDirectMessageChannelTest {

    @Test
    void toProtoDirectMessageChannel_정상_변환() {
        DirectMessage directMessage = new DirectMessage(1L, "Bob", false, null, null, null);

        DirectMessageProto.DirectMessageChannel result =
                DirectMessageMapper.toProtoDirectMessageChannel(directMessage);

        assertThat(result.getDmId()).isEqualTo(1L);
        assertThat(result.getChannelName()).isEqualTo("Bob");
        assertThat(result.getIsGroup()).isFalse();
    }

    @Test
    void toProtoDirectMessageChannel_null이면_예외발생() {
        assertThatThrownBy(() -> DirectMessageMapper.toProtoDirectMessageChannel(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toProtoDirectMessageChannel_id가_null이면_0으로_처리() {
        DirectMessage directMessage = new DirectMessage(null, "Bob", false, null, null, null);

        DirectMessageProto.DirectMessageChannel result =
                DirectMessageMapper.toProtoDirectMessageChannel(directMessage);

        assertThat(result.getDmId()).isEqualTo(0L);
    }

    @Test
    void toProtoDirectMessageChannel_channelName이_null이면_빈_문자열로_처리() {
        DirectMessage directMessage = new DirectMessage(1L, null, false, null, null, null);

        DirectMessageProto.DirectMessageChannel result =
                DirectMessageMapper.toProtoDirectMessageChannel(directMessage);

        assertThat(result.getChannelName()).isEmpty();
    }

    @Test
    void toProtoDirectMessageChannel_isGroup이_true이면_proto에_true_설정() {
        DirectMessage directMessage = new DirectMessage(1L, "Bob,Carol", true, null, null, null);

        DirectMessageProto.DirectMessageChannel result =
                DirectMessageMapper.toProtoDirectMessageChannel(directMessage);

        assertThat(result.getIsGroup()).isTrue();
    }

    @Test
    void toProtoDirectMessageChannel_isGroup이_false이면_proto에_false_설정() {
        DirectMessage directMessage = new DirectMessage(1L, "Bob", false, null, null, null);

        DirectMessageProto.DirectMessageChannel result =
                DirectMessageMapper.toProtoDirectMessageChannel(directMessage);

        assertThat(result.getIsGroup()).isFalse();
    }

    @Test
    void toProtoDirectMessageChannel_isGroup이_null이면_proto에_false_설정() {
        DirectMessage directMessage = new DirectMessage(1L, "Bob", null, null, null, null);

        DirectMessageProto.DirectMessageChannel result =
                DirectMessageMapper.toProtoDirectMessageChannel(directMessage);

        assertThat(result.getIsGroup()).isFalse();
    }

    @Test
    void toProtoDirectMessageChannel_channelImageUrl이_proto에_설정() {
        DirectMessage directMessage = new DirectMessage(1L, "Bob,Carol", true, "channels/1/abc.png", null, null);

        DirectMessageProto.DirectMessageChannel result =
                DirectMessageMapper.toProtoDirectMessageChannel(directMessage);

        assertThat(result.getChannelImageUrl()).isEqualTo("channels/1/abc.png");
    }

    @Test
    void toProtoDirectMessageChannel_channelImageUrl이_null이면_빈문자열로_처리() {
        DirectMessage directMessage = new DirectMessage(1L, "Bob", false, null, null, null);

        DirectMessageProto.DirectMessageChannel result =
                DirectMessageMapper.toProtoDirectMessageChannel(directMessage);

        assertThat(result.getChannelImageUrl()).isEmpty();
    }
}
