package com.danmalgi.backend.dm.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.grpc.validator.DirectMessageGrpcValidator;
import com.danmalgi.backend.dm.service.DirectMessageService;
import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectMessageGrpcGetDirectMessageChannelTest {

    @Mock
    private DirectMessageService directMessageService;

    @Mock
    private DirectMessageGrpcValidator directMessageGrpcValidator;

    @Mock
    private StreamObserver<DirectMessageProto.GetDirectMessageChannelResponse> responseObserver;

    private DirectMessageGrpc directMessageGrpc;

    @BeforeEach
    void setUp() {
        directMessageGrpc = new DirectMessageGrpc(directMessageService, directMessageGrpcValidator);
    }

    @Test
    void getDirectMessageChannel_검증후_서비스를_호출하고_채널을_응답() {
        DirectMessageProto.GetDirectMessageChannelRequest request = request(10L);
        when(directMessageService.getDirectMessageChannel(1L, 10L))
                .thenReturn(new DirectMessage(10L, "Study Room", true, "https://signed/channels/10/abc.webp", List.of(), null));

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.getDirectMessageChannel(request, responseObserver));

        verify(directMessageGrpcValidator).validateGetDirectMessageChannelRequest(request);
        ArgumentCaptor<DirectMessageProto.GetDirectMessageChannelResponse> captor =
                ArgumentCaptor.forClass(DirectMessageProto.GetDirectMessageChannelResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        DirectMessageProto.DirectMessageChannel channel = captor.getValue().getDirectMessageChannel();
        assertThat(channel.getDmId()).isEqualTo(10L);
        assertThat(channel.getChannelName()).isEqualTo("Study Room");
        assertThat(channel.getIsGroup()).isTrue();
        assertThat(channel.getChannelImageUrl()).isEqualTo("https://signed/channels/10/abc.webp");
    }

    @Test
    void getDirectMessageChannel_검증에_실패하면_서비스를_호출하지_않음() {
        DirectMessageProto.GetDirectMessageChannelRequest request = request(0L);
        doThrow(new IllegalArgumentException("dm_id must be positive"))
                .when(directMessageGrpcValidator).validateGetDirectMessageChannelRequest(request);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);

        assertThatThrownBy(() -> ctx.run(() -> directMessageGrpc.getDirectMessageChannel(request, responseObserver)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(directMessageService, responseObserver);
    }

    private DirectMessageProto.GetDirectMessageChannelRequest request(long dmId) {
        return DirectMessageProto.GetDirectMessageChannelRequest.newBuilder()
                .setDmId(dmId)
                .build();
    }
}
