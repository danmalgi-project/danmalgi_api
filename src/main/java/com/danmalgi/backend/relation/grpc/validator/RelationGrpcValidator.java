package com.danmalgi.backend.relation.grpc.validator;

import com.danmalgi.backend.external.relationship.v1.RelationshipProto;
import org.springframework.stereotype.Component;

@Component
public class RelationGrpcValidator {
    public void validateAddRelationshipRequest(RelationshipProto.AddRelationshipRequest request) {
        if (request.getName().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }

        if (request.getTag().isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
    }

    public void validateUpdateRelationshipStatusRequest(
            long relationshipId,
            RelationshipProto.RelationshipStatus status
    ) {
        if (relationshipId <= 0) {
            throw new IllegalArgumentException("relationshipId must be positive");
        }

        if (status == RelationshipProto.RelationshipStatus.UNRECOGNIZED) {
            throw new IllegalArgumentException("relationshipStatus is invalid");
        }
    }
}
