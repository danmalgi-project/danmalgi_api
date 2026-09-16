package com.danmalgi.backend.dm.grpc;

import static org.mockito.Mockito.verify;

import com.danmalgi.backend.dm.grpc.validator.DirectMessageGrpcValidator;
import com.danmalgi.backend.dm.service.DirectMessageService;
import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.google.protobuf.Empty;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectMessageGrpcLeaveDirectMessageChannelTest {

    @Mock
    private DirectMessageService directMessageService;

    @Mock
    private DirectMessageGrpcValidator directMessageGrpcValidator;

    @Mock
    private StreamObserver<Empty> responseObserver;

    private DirectMessageGrpc directMessageGrpc;

    @BeforeEach
    void setUp() {
        directMessageGrpc = new DirectMessageGrpc(directMessageService, directMessageGrpcValidator);
    }

    @Test
    void leaveDirectMessageChannel_callsValidatorServiceAndReturnsEmpty() {
        DirectMessageProto.LeaveDirectMessageChannelRequest request =
                DirectMessageProto.LeaveDirectMessageChannelRequest.newBuilder()
                        .setDmId(10L)
                        .build();

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.leaveDirectMessageChannel(request, responseObserver));

        verify(directMessageGrpcValidator).validateLeaveDirectMessageChannelRequest(request);
        verify(directMessageService).leaveChannel(1L, 10L);
        verify(responseObserver).onNext(Empty.getDefaultInstance());
        verify(responseObserver).onCompleted();
    }
}
