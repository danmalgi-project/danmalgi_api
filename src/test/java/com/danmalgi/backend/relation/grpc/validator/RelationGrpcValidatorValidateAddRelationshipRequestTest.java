package com.danmalgi.backend.relation.grpc.validator;

import com.danmalgi.backend.external.relationship.v1.RelationshipProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationGrpcValidatorValidateAddRelationshipRequestTest {

    private final RelationGrpcValidator validator = new RelationGrpcValidator();

    @Test
    void validateAddRelationshipRequest_정상_요청이면_예외없음() {
        RelationshipProto.AddRelationshipRequest request = RelationshipProto.AddRelationshipRequest.newBuilder()
                .setName("Bob")
                .setTag("00002")
                .build();

        assertThatNoException().isThrownBy(() -> validator.validateAddRelationshipRequest(request));
    }

    @Test
    void validateAddRelationshipRequest_name이_blank이면_예외발생() {
        RelationshipProto.AddRelationshipRequest request = RelationshipProto.AddRelationshipRequest.newBuilder()
                .setName("")
                .setTag("00002")
                .build();

        assertThatThrownBy(() -> validator.validateAddRelationshipRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void validateAddRelationshipRequest_tag가_blank이면_예외발생() {
        RelationshipProto.AddRelationshipRequest request = RelationshipProto.AddRelationshipRequest.newBuilder()
                .setName("Bob")
                .setTag("")
                .build();

        assertThatThrownBy(() -> validator.validateAddRelationshipRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tag");
    }
}
