package com.danmalgi.backend.relation.grpc.mapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.danmalgi.backend.relation.domain.model.Relation;
import com.danmalgi.backend.external.relationship.v1.RelationshipProto;
import com.danmalgi.backend.user.grpc.mapper.UserGrpcMapper;

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

        return RelationshipProto.Relationship.newBuilder()
                .setRelationshipId(Optional.ofNullable(relationship.getId()).orElse(0L))
                .setUser(UserGrpcMapper.toProtoUser(relationship.getUser()))
                .setRelationshipStatus(relationshipStatus)
                .build();
    }
}
