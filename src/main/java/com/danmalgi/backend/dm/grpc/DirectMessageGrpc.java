package com.danmalgi.backend.dm.grpc;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.grpc.mapper.DirectMessageMapper;
import com.danmalgi.backend.dm.grpc.validator.DirectMessageGrpcValidator;
import com.danmalgi.backend.dm.service.DirectMessageService;
import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import com.danmalgi.backend.external.dm.v1.DirectMessageServiceGrpc.DirectMessageServiceImplBase;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;

@GrpcService
@RequiredArgsConstructor
public class DirectMessageGrpc extends DirectMessageServiceImplBase {
    private final DirectMessageService directMessageService;
    private final DirectMessageGrpcValidator directMessageGrpcValidator;

    @Override
    public void getDirectMessageChannelList(Empty request, StreamObserver<DirectMessageProto.GetDirectMessageChannelListResponse> responseObserver) {
        Long userId = GrpcContext.USER_ID.get();

        List<DirectMessage> directMessageChannels = directMessageService.getDirectMessageChannels(userId);

        DirectMessageProto.GetDirectMessageChannelListResponse response = DirectMessageProto.GetDirectMessageChannelListResponse.newBuilder()
                .addAllItems(DirectMessageMapper.toProtoDirectMessageChannelListItems(directMessageChannels))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void createDirectMessageChannel(DirectMessageProto.CreateDirectMessageChannelRequest request, StreamObserver<DirectMessageProto.CreateDirectMessageChannelResponse> responseObserver) {
        Long userId = GrpcContext.USER_ID.get();

        directMessageGrpcValidator.validateCreateDirectMessageChannelRequest(request, userId);

        DirectMessage directMessageChannel = directMessageService.createChannel(
                userId,
                request.getFriendIdsList()
        );

        DirectMessageProto.CreateDirectMessageChannelResponse response = DirectMessageProto.CreateDirectMessageChannelResponse.newBuilder()
                .setDirectMessageChannel(DirectMessageMapper.toProtoDirectMessageChannel(directMessageChannel))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void uploadChannelImage(
            DirectMessageProto.UploadChannelImageRequest request,
            StreamObserver<DirectMessageProto.UploadChannelImageResponse> responseObserver
    ) {
        Long userId = GrpcContext.USER_ID.get();

        directMessageGrpcValidator.validateUploadChannelImageRequest(request);

        DirectMessage directMessage = directMessageService.updateChannel(
                userId,
                request.getDmId(),
                null,
                request.getImage().toByteArray(),
                request.getExtension()
        );

        DirectMessageProto.UploadChannelImageResponse response = DirectMessageProto.UploadChannelImageResponse.newBuilder()
                .setDirectMessageChannel(DirectMessageMapper.toProtoDirectMessageChannel(directMessage))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void updateDirectMessageChannel(
            DirectMessageProto.UpdateDirectMessageChannelRequest request,
            StreamObserver<DirectMessageProto.UpdateDirectMessageChannelResponse> responseObserver
    ) {
        Long userId = GrpcContext.USER_ID.get();

        directMessageGrpcValidator.validateUpdateDirectMessageChannelRequest(request);

        DirectMessage directMessage = directMessageService.updateChannel(
                userId,
                request.getDmId(),
                request.getChannelName(),
                request.getImage().isEmpty() ? null : request.getImage().toByteArray(),
                request.getExtension()
        );

        DirectMessageProto.UpdateDirectMessageChannelResponse response =
                DirectMessageProto.UpdateDirectMessageChannelResponse.newBuilder()
                        .setDirectMessageChannel(DirectMessageMapper.toProtoDirectMessageChannel(directMessage))
                        .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void leaveDirectMessageChannel(
            DirectMessageProto.LeaveDirectMessageChannelRequest request,
            StreamObserver<Empty> responseObserver
    ) {
        Long userId = GrpcContext.USER_ID.get();

        directMessageGrpcValidator.validateLeaveDirectMessageChannelRequest(request);
        directMessageService.leaveChannel(userId, request.getDmId());

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
