package com.danmalgi.backend.relation.grpc.mapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.danmalgi.backend.relation.domain.model.Relation;
import com.danmalgi.backend.external.relationship.v1.RelationshipProto;
import com.danmalgi.backend.user.grpc.mapper.UserGrpcMapper;
import com.google.protobuf.Timestamp;

public class RelationGrpcMapper {
    private RelationGrpcMapper() {}

    public static List<RelationshipProto.Relationship> toProtoRelationships(
            List<Relation> relationships
    ) {
        if (relationships == null || relationships.isEmpty()) {
            return Collections.emptyList();
        }

        return relationships.stream()
                .map(RelationGrpcMapper::toProtoRelationship)
                .collect(Collectors.toList());
    }

    public static RelationshipProto.Relationship toProtoRelationship(Relation relationship) {
        if (relationship == null) {
            throw new IllegalArgumentException("relationship must not be null");
        }

        RelationshipProto.RelationshipStatus relationshipStatus =
                RelationshipProto.RelationshipStatus.forNumber(relationship.getStatus());
        if (relationshipStatus == null) {
            throw new IllegalStateException("invalid relationship status: " + relationship.getStatus());
        }

        RelationshipProto.Relationship.Builder builder = RelationshipProto.Relationship.newBuilder()
                .setRelationshipId(Optional.ofNullable(relationship.getId()).orElse(0L))
                .setUser(UserGrpcMapper.toProtoUser(relationship.getUser()))
                .setRelationshipStatus(relationshipStatus);

        Instant requestedAt = relationship.getRequestedAt();
        if (requestedAt != null) {
            builder.setRequestedAt(Timestamp.newBuilder()
                    .setSeconds(requestedAt.getEpochSecond())
                    .setNanos(requestedAt.getNano())
                    .build());
        }

        return builder.build();
    }
}
