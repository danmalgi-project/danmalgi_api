package com.danmalgi.backend.relation.grpc.validator;

import com.danmalgi.backend.external.relationship.v1.RelationshipProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationGrpcValidatorValidateUpdateRelationshipStatusRequestTest {

    private final RelationGrpcValidator validator = new RelationGrpcValidator();

    @Test
    void validateUpdateRelationshipStatusRequest_정상_요청이면_예외없음() {
        assertThatNoException().isThrownBy(() ->
                validator.validateUpdateRelationshipStatusRequest(1L, RelationshipProto.RelationshipStatus.RELATION_ACCEPT)
        );
    }

    @Test
    void validateUpdateRelationshipStatusRequest_relationshipId가_0이면_예외발생() {
        assertThatThrownBy(() ->
                validator.validateUpdateRelationshipStatusRequest(0L, RelationshipProto.RelationshipStatus.RELATION_ACCEPT)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relationshipId");
    }

    @Test
    void validateUpdateRelationshipStatusRequest_relationshipId가_음수이면_예외발생() {
        assertThatThrownBy(() ->
                validator.validateUpdateRelationshipStatusRequest(-1L, RelationshipProto.RelationshipStatus.RELATION_ACCEPT)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relationshipId");
    }

    @Test
    void validateUpdateRelationshipStatusRequest_status가_UNRECOGNIZED이면_예외발생() {
        assertThatThrownBy(() ->
                validator.validateUpdateRelationshipStatusRequest(1L, RelationshipProto.RelationshipStatus.UNRECOGNIZED)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relationshipStatus");
    }
}
