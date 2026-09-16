package com.danmalgi.backend.dm.grpc;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.domain.model.LastMessage;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageGrpcGetDirectMessageChannelListTest {

    @Mock
    private DirectMessageService directMessageService;

    @Mock
    private DirectMessageGrpcValidator directMessageGrpcValidator;

    @Mock
    private StreamObserver<DirectMessageProto.GetDirectMessageChannelListResponse> responseObserver;

    private DirectMessageGrpc directMessageGrpc;

    @BeforeEach
    void setUp() {
        directMessageGrpc = new DirectMessageGrpc(directMessageService, directMessageGrpcValidator);
    }

    @Test
    void getDirectMessageChannelList_채널_목록_반환() {
        // Service 가 이미 변환된 결과 반환 — grpc 는 그대로 전달
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, null),
                new DirectMessage(2L, "Carol", false, null, null, null)
        );
        when(directMessageService.getDirectMessageChannels(1L)).thenReturn(channels);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.getDirectMessageChannelList(Empty.getDefaultInstance(), responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getItemsCount() == 2
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void getDirectMessageChannelList_service가_반환한_presigned_URL이_그대로_응답에_들어간다() {
        // Service 가 이미 presigned URL 로 변환한 결과 반환 — grpc 는 추가 변환 없음
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", true,
                        "https://r2.example.com/channels/1/abc.webp?sig=xyz", null, null)
        );
        when(directMessageService.getDirectMessageChannels(1L)).thenReturn(channels);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.getDirectMessageChannelList(Empty.getDefaultInstance(), responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getItemsCount() == 1 &&
                        response.getItems(0).getChannel().getChannelImageUrl()
                                .equals("https://r2.example.com/channels/1/abc.webp?sig=xyz")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void getDirectMessageChannelList_결과가_없으면_빈_목록으로_응답() {
        when(directMessageService.getDirectMessageChannels(1L)).thenReturn(List.of());

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.getDirectMessageChannelList(Empty.getDefaultInstance(), responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getItemsCount() == 0
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void getDirectMessageChannelList_lastMessage가_있으면_응답에_채워지고_null이면_unset() {
        Instant createdAt = Instant.ofEpochSecond(1_700_000_000L, 123);
        LastMessage lastMessage = new LastMessage(10L, "안녕하세요", 2L, createdAt);
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", false, null, null, lastMessage),
                new DirectMessage(2L, "Carol", false, null, null, null)
        );
        when(directMessageService.getDirectMessageChannels(1L)).thenReturn(channels);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.getDirectMessageChannelList(Empty.getDefaultInstance(), responseObserver));

        verify(responseObserver).onNext(argThat(response -> {
            if (response.getItemsCount() != 2) {
                return false;
            }
            DirectMessageProto.DirectMessageChannelListItem first = response.getItems(0);
            DirectMessageProto.DirectMessageChannelListItem second = response.getItems(1);
            return first.getChannel().getDmId() == 1L
                    && first.hasLastMessage()
                    && first.getLastMessage().getMessageId() == 10L
                    && first.getLastMessage().getContent().equals("안녕하세요")
                    && first.getLastMessage().getSenderId() == 2L
                    && first.getLastMessage().getCreatedAt().getSeconds() == 1_700_000_000L
                    && first.getLastMessage().getCreatedAt().getNanos() == 123
                    && second.getChannel().getDmId() == 2L
                    && !second.hasLastMessage();
        }));
        verify(responseObserver).onCompleted();
    }

    @Test
    void getDirectMessageChannelList_lastMessage와_channelImageUrl이_함께_응답에_매핑된다() {
        Instant createdAt = Instant.ofEpochSecond(1_700_000_500L, 0);
        LastMessage lastMessage = new LastMessage(20L, "사진 보냈어요", 3L, createdAt);
        // Service 가 이미 presigned URL 로 변환한 결과 반환 — grpc 는 추가 변환 없음
        List<DirectMessage> channels = List.of(
                new DirectMessage(1L, "Bob", true,
                        "https://r2.example.com/channels/1/abc.webp?sig=xyz", null, lastMessage)
        );
        when(directMessageService.getDirectMessageChannels(1L)).thenReturn(channels);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> directMessageGrpc.getDirectMessageChannelList(Empty.getDefaultInstance(), responseObserver));

        verify(responseObserver).onNext(argThat(response -> {
            if (response.getItemsCount() != 1) {
                return false;
            }
            DirectMessageProto.DirectMessageChannelListItem item = response.getItems(0);
            return item.getChannel().getChannelImageUrl().equals("https://r2.example.com/channels/1/abc.webp?sig=xyz")
                    && item.hasLastMessage()
                    && item.getLastMessage().getMessageId() == 20L
                    && item.getLastMessage().getContent().equals("사진 보냈어요")
                    && item.getLastMessage().getSenderId() == 3L
                    && item.getLastMessage().getCreatedAt().getSeconds() == 1_700_000_500L;
        }));
        verify(responseObserver).onCompleted();
    }
}
