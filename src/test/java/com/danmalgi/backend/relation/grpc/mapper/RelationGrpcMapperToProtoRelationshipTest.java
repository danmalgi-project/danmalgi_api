package com.danmalgi.backend.relation.grpc.mapper;

import com.danmalgi.backend.external.relationship.v1.RelationshipProto;
import com.danmalgi.backend.relation.domain.model.Relation;
import com.danmalgi.backend.relation.domain.model.RelationStatus;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationGrpcMapperToProtoRelationshipTest {

    private User createUser(Long id, String name, String tag) {
        return new User(id, "test@test.com", name, tag, null, UserStatus.ACTIVE.getNumber());
    }

    @Test
    void toProtoRelationship_정상_변환() {
        User user = createUser(2L, "Bob", "00002");
        Relation relation = new Relation(1L, user, RelationStatus.PENDING.forNumber(), null);

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
        Relation relation = new Relation(1L, user, 999, null);

        assertThatThrownBy(() -> RelationGrpcMapper.toProtoRelationship(relation))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toProtoRelationship_requestedAt이_있으면_Timestamp로_변환() {
        User user = createUser(2L, "Bob", "00002");
        Instant requestedAt = Instant.ofEpochSecond(1_700_000_000L, 123_000_000);
        Relation relation = new Relation(1L, user, RelationStatus.PENDING.forNumber(), requestedAt);

        RelationshipProto.Relationship result = RelationGrpcMapper.toProtoRelationship(relation);

        assertThat(result.hasRequestedAt()).isTrue();
        assertThat(result.getRequestedAt().getSeconds()).isEqualTo(requestedAt.getEpochSecond());
        assertThat(result.getRequestedAt().getNanos()).isEqualTo(requestedAt.getNano());
    }

    @Test
    void toProtoRelationship_requestedAt이_null이면_필드_미설정() {
        User user = createUser(2L, "Bob", "00002");
        Relation relation = new Relation(1L, user, RelationStatus.PENDING.forNumber(), null);

        RelationshipProto.Relationship result = RelationGrpcMapper.toProtoRelationship(relation);

        assertThat(result.hasRequestedAt()).isFalse();
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
                new Relation(1L, user, RelationStatus.PENDING.forNumber(), null),
                new Relation(2L, user, RelationStatus.REJECT.forNumber(), null)
        );

        List<RelationshipProto.Relationship> result = RelationGrpcMapper.toProtoRelationships(relations);

        assertThat(result).hasSize(2);
    }
}
