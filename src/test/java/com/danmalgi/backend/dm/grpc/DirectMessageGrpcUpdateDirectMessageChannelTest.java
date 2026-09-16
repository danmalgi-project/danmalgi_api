package com.danmalgi.backend.dm.grpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.grpc.validator.DirectMessageGrpcValidator;
import com.danmalgi.backend.dm.service.DirectMessageService;
import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.google.protobuf.ByteString;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectMessageGrpcUpdateDirectMessageChannelTest {

    @Mock
    private DirectMessageService directMessageService;

    @Mock
    private DirectMessageGrpcValidator directMessageGrpcValidator;

    @Mock
    private StreamObserver<DirectMessageProto.UpdateDirectMessageChannelResponse> responseObserver;

    private DirectMessageGrpc directMessageGrpc;

    @BeforeEach
    void setUp() {
        directMessageGrpc = new DirectMessageGrpc(directMessageService, directMessageGrpcValidator);
    }

    @Test
    void updateDirectMessageChannel_validator_service_호출하고_service_반환의_presigned_URL_그대로_응답() {
        DirectMessageProto.UpdateDirectMessageChannelRequest request =
                DirectMessageProto.UpdateDirectMessageChannelRequest.newBuilder()
                        .setDmId(10L)
                        .setChannelName("Study Room")
                        .setImage(ByteString.copyFrom(new byte[]{1, 2, 3}))
                        .setExtension("png")
                        .build();

        // Service 가 이미 presigned URL 로 변환된 DirectMessage 반환
        when(directMessageService.updateChannel(eq(1L), eq(10L), eq("Study Room"), any(byte[].class), eq("png")))
                .thenReturn(new DirectMessage(10L, "Study Room", true,
                        "https://r2.example.com/channels/10/abc.webp?sig=xyz", List.of(), null));

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.updateDirectMessageChannel(request, responseObserver));

        verify(directMessageGrpcValidator).validateUpdateDirectMessageChannelRequest(request);
        verify(directMessageService).updateChannel(eq(1L), eq(10L), eq("Study Room"), any(byte[].class), eq("png"));
        verify(responseObserver).onNext(argThat(response ->
                response.getDirectMessageChannel().getDmId() == 10L
                        && response.getDirectMessageChannel().getChannelName().equals("Study Room")
                        && response.getDirectMessageChannel().getChannelImageUrl()
                        .equals("https://r2.example.com/channels/10/abc.webp?sig=xyz")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void updateDirectMessageChannel_doesNotCallServiceWhenValidatorThrows() {
        DirectMessageProto.UpdateDirectMessageChannelRequest request =
                DirectMessageProto.UpdateDirectMessageChannelRequest.newBuilder()
                        .setDmId(10L)
                        .build();
        Mockito.doThrow(new IllegalArgumentException("channel_name or image must be provided"))
                .when(directMessageGrpcValidator).validateUpdateDirectMessageChannelRequest(request);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);

        try {
            ctx.run(() -> directMessageGrpc.updateDirectMessageChannel(request, responseObserver));
        } catch (IllegalArgumentException ignored) {
        }

        verify(directMessageService, never()).updateChannel(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(),
                any(),
                any()
        );
    }
}
