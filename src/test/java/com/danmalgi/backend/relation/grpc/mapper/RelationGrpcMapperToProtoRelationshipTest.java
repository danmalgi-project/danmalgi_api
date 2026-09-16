package com.danmalgi.backend.relation.grpc.mapper;

import com.danmalgi.backend.external.relationship.v1.RelationshipProto;
import com.danmalgi.backend.relation.domain.model.Relation;
import com.danmalgi.backend.relation.domain.model.RelationStatus;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationGrpcMapperToProtoRelationshipTest {

    private User createUser(Long id, String name, String tag) {
        return new User(id, "test@test.com", name, tag, null, "oauth-id",
                OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber());
    }

    @Test
    void toProtoRelationship_정상_변환() {
        User user = createUser(2L, "Bob", "00002");
        Relation relation = new Relation(1L, user, RelationStatus.PENDING.forNumber());

        RelationshipProto.Relationship result = RelationGrpcMapper.toProtoRelationship(relation);

        assertThat(result.getRelationshipId()).isEqualTo(1L);
        assertThat(result.getRelationshipStatus()).isEqualTo(RelationshipProto.RelationshipStatus.RELATION_PENDING);
        assertThat(result.getUser().getName()).isEqualTo("Bob");
    }

    @Test
    void toProtoRelationship_null이면_예외발생() {
        assertThatThrownBy(() -> RelationGrpcMapper.toProtoRelationship(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toProtoRelationship_유효하지_않은_status이면_예외발생() {
        User user = createUser(2L, "Bob", "00002");
        Relation relation = new Relation(1L, user, 999);

        assertThatThrownBy(() -> RelationGrpcMapper.toProtoRelationship(relation))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toProtoRelationships_빈_목록이면_빈_목록_반환() {
        List<RelationshipProto.Relationship> result = RelationGrpcMapper.toProtoRelationships(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void toProtoRelationships_null이면_빈_목록_반환() {
        List<RelationshipProto.Relationship> result = RelationGrpcMapper.toProtoRelationships(null);

        assertThat(result).isEmpty();
    }

    @Test
    void toProtoRelationships_목록_변환() {
        User user = createUser(2L, "Bob", "00002");
        List<Relation> relations = List.of(
                new Relation(1L, user, RelationStatus.PENDING.forNumber()),
                new Relation(2L, user, RelationStatus.REJECT.forNumber())
        );

        List<RelationshipProto.Relationship> result = RelationGrpcMapper.toProtoRelationships(relations);

        assertThat(result).hasSize(2);
    }
}
