package com.danmalgi.backend.relation.grpc;

import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.relation.domain.model.Relation;
import com.danmalgi.backend.relation.grpc.mapper.RelationGrpcMapper;
import com.danmalgi.backend.relation.grpc.validator.RelationGrpcValidator;
import com.danmalgi.backend.relation.service.RelationService;
import com.danmalgi.backend.external.relationship.v1.RelationshipProto.*;
import com.danmalgi.backend.external.relationship.v1.RelationshipServiceGrpc;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;

@GrpcService
@RequiredArgsConstructor
public class RelationGrpc extends RelationshipServiceGrpc.RelationshipServiceImplBase {
    private final RelationGrpcValidator relationValidator;
    private final RelationService relationService;

    @Override
    public void addRelationship(AddRelationshipRequest request, StreamObserver<AddRelationshipResponse> responseObserver) {
        relationValidator.validateAddRelationshipRequest(request);

        Long userId = GrpcContext.USER_ID.get();

        List<Relation> result = relationService.addRelationship(userId, request.getName(), request.getTag());

        AddRelationshipResponse response = AddRelationshipResponse.newBuilder()
                .addAllRelationships(RelationGrpcMapper.toProtoRelationships(result))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

    }

    @Override
    public void getIncomingRelationshipList(Empty request, StreamObserver<GetIncomingRelationshipListResponse> responseObserver) {
        Long userId = GrpcContext.USER_ID.get();

        List<Relation> result = relationService.getIncomingRequests(userId);

        GetIncomingRelationshipListResponse response = GetIncomingRelationshipListResponse.newBuilder()
                .addAllRelationships(RelationGrpcMapper.toProtoRelationships(result))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getOutgoingRelationshipList(Empty request, StreamObserver<GetOutgoingRelationshipListResponse> responseObserver) {
        Long userId = GrpcContext.USER_ID.get();

        List<Relation> result = relationService.getOutgoingRequest(userId);

        GetOutgoingRelationshipListResponse response = GetOutgoingRelationshipListResponse.newBuilder()
                .addAllRelationships(RelationGrpcMapper.toProtoRelationships(result))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void updateIncomingRelationshipStatus(UpdateIncomingRelationshipStatusRequest request, StreamObserver<UpdateIncomingRelationshipStatusResponse> responseObserver) {
        relationValidator.validateUpdateRelationshipStatusRequest(request.getRelationshipId(), request.getRelationshipStatus());

        Long userId = GrpcContext.USER_ID.get();

        List<Relation> result = relationService.updateIncomingRequest(
                userId,
                request.getRelationshipId(),
                request.getRelationshipStatusValue()
        );

        UpdateIncomingRelationshipStatusResponse response = UpdateIncomingRelationshipStatusResponse.newBuilder()
                .addAllRelationships(RelationGrpcMapper.toProtoRelationships(result))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void updateOutgoingRelationshipStatus(UpdateOutgoingRelationshipStatusRequest request, StreamObserver<UpdateOutgoingRelationshipStatusResponse> responseObserver) {
        relationValidator.validateUpdateRelationshipStatusRequest(request.getRelationshipId(), request.getRelationshipStatus());

        Long userId = GrpcContext.USER_ID.get();

        List<Relation> result = relationService.updateOutgoingRequest(
                userId,
                request.getRelationshipId(),
                request.getRelationshipStatusValue()
        );

        UpdateOutgoingRelationshipStatusResponse response = UpdateOutgoingRelationshipStatusResponse.newBuilder()
                .addAllRelationships(RelationGrpcMapper.toProtoRelationships(result))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
