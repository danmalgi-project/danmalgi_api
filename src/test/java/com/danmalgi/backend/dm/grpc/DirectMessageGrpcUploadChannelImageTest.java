package com.danmalgi.backend.dm.grpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectMessageGrpcUploadChannelImageTest {

    @Mock
    private DirectMessageService directMessageService;

    @Mock
    private DirectMessageGrpcValidator directMessageGrpcValidator;

    @Mock
    private StreamObserver<DirectMessageProto.UploadChannelImageResponse> responseObserver;

    private DirectMessageGrpc directMessageGrpc;

    @BeforeEach
    void setUp() {
        directMessageGrpc = new DirectMessageGrpc(directMessageService, directMessageGrpcValidator);
    }

    private DirectMessageProto.UploadChannelImageRequest sampleRequest() {
        return DirectMessageProto.UploadChannelImageRequest.newBuilder()
                .setDmId(10L)
                .setImage(ByteString.copyFrom(new byte[]{1, 2, 3}))
                .setExtension("png")
                .build();
    }

    @Test
    void uploadChannelImage_validator_service_호출하고_service_반환의_presigned_URL_그대로_응답() {
        DirectMessageProto.UploadChannelImageRequest request = sampleRequest();
        // Service 가 이미 presigned URL 로 변환된 DirectMessage 반환
        when(directMessageService.updateChannel(eq(1L), eq(10L), isNull(), any(byte[].class), eq("png")))
                .thenReturn(new DirectMessage(10L, "Study Room", true,
                        "https://r2.example.com/channels/10/abc.webp?sig=xyz", null, null));

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.uploadChannelImage(request, responseObserver));

        verify(directMessageGrpcValidator).validateUploadChannelImageRequest(request);
        verify(directMessageService).updateChannel(eq(1L), eq(10L), isNull(), any(byte[].class), eq("png"));
        verify(responseObserver).onNext(argThat(response ->
                response.getDirectMessageChannel().getDmId() == 10L
                        && response.getDirectMessageChannel().getIsGroup()
                        && response.getDirectMessageChannel().getChannelImageUrl()
                        .equals("https://r2.example.com/channels/10/abc.webp?sig=xyz")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void uploadChannelImage_doesNotCallServiceWhenValidatorThrows() {
        DirectMessageProto.UploadChannelImageRequest request = sampleRequest();
        Mockito.doThrow(new IllegalArgumentException("image must not be empty"))
                .when(directMessageGrpcValidator).validateUploadChannelImageRequest(request);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);

        try {
            ctx.run(() -> directMessageGrpc.uploadChannelImage(request, responseObserver));
        } catch (IllegalArgumentException ignored) {
        }

        verify(directMessageService, never()).updateChannel(
                anyLong(),
                anyLong(),
                any(),
                any(byte[].class),
                any()
        );
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void uploadChannelImage_service가_빈_channelImageUrl_반환시_그대로_응답() {
        DirectMessageProto.UploadChannelImageRequest request = sampleRequest();
        when(directMessageService.updateChannel(eq(1L), eq(10L), isNull(), any(byte[].class), eq("png")))
                .thenReturn(new DirectMessage(10L, "Study Room", true, null, null, null));

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.uploadChannelImage(request, responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getDirectMessageChannel().getChannelImageUrl().isEmpty()
        ));
        verify(responseObserver).onCompleted();
    }
}
