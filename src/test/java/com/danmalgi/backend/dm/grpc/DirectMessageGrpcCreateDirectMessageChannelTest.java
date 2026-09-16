package com.danmalgi.backend.dm.grpc;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.grpc.validator.DirectMessageGrpcValidator;
import com.danmalgi.backend.dm.service.DirectMessageService;
import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageGrpcCreateDirectMessageChannelTest {

    @Mock
    private DirectMessageService directMessageService;

    @Mock
    private DirectMessageGrpcValidator directMessageGrpcValidator;

    @Mock
    private StreamObserver<DirectMessageProto.CreateDirectMessageChannelResponse> responseObserver;

    private DirectMessageGrpc directMessageGrpc;

    @BeforeEach
    void setUp() {
        directMessageGrpc = new DirectMessageGrpc(directMessageService, directMessageGrpcValidator);
    }

    @Test
    void createDirectMessageChannel_정상_요청이면_onNext와_onCompleted_호출() {
        DirectMessageProto.CreateDirectMessageChannelRequest request =
                DirectMessageProto.CreateDirectMessageChannelRequest.newBuilder()
                        .addAllFriendIds(List.of(2L))
                        .build();

        when(directMessageService.createChannel(1L, List.of(2L)))
                .thenReturn(new DirectMessage(10L, "Bob", false, null, null, null));

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.createDirectMessageChannel(request, responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getDirectMessageChannel().getDmId() == 10L &&
                response.getDirectMessageChannel().getChannelName().equals("Bob") &&
                !response.getDirectMessageChannel().getIsGroup()
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void createDirectMessageChannel_validator를_요청자_id와_함께_호출한다() {
        DirectMessageProto.CreateDirectMessageChannelRequest request =
                DirectMessageProto.CreateDirectMessageChannelRequest.newBuilder()
                        .addAllFriendIds(List.of(2L))
                        .build();

        when(directMessageService.createChannel(1L, List.of(2L)))
                .thenReturn(new DirectMessage(10L, "Bob", false, null, null, null));

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.createDirectMessageChannel(request, responseObserver));

        verify(directMessageGrpcValidator).validateCreateDirectMessageChannelRequest(request, 1L);
    }

    @Test
    void createDirectMessageChannel_validator가_거부하면_service와_observer가_호출되지_않는다() {
        DirectMessageProto.CreateDirectMessageChannelRequest request =
                DirectMessageProto.CreateDirectMessageChannelRequest.newBuilder()
                        .build();

        doThrow(new IllegalArgumentException("friend_ids must not be empty"))
                .when(directMessageGrpcValidator)
                .validateCreateDirectMessageChannelRequest(request, 1L);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);

        assertThatThrownBy(() ->
                ctx.call(() -> {
                    directMessageGrpc.createDirectMessageChannel(request, responseObserver);
                    return null;
                })
        ).isInstanceOf(IllegalArgumentException.class);

        verify(directMessageService, never()).createChannel(any(), any());
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }
}
