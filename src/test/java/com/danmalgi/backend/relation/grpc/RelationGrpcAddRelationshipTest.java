package com.danmalgi.backend.relation.grpc;

import com.danmalgi.backend.external.relationship.v1.RelationshipProto;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.relation.domain.model.Relation;
import com.danmalgi.backend.relation.domain.model.RelationStatus;
import com.danmalgi.backend.relation.grpc.validator.RelationGrpcValidator;
import com.danmalgi.backend.relation.service.RelationService;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationGrpcAddRelationshipTest {

    @Mock
    private RelationGrpcValidator relationValidator;

    @Mock
    private RelationService relationService;

    @Mock
    private StreamObserver<RelationshipProto.AddRelationshipResponse> responseObserver;

    private RelationGrpc relationGrpc;

    @BeforeEach
    void setUp() {
        relationGrpc = new RelationGrpc(relationValidator, relationService);
    }

    @Test
    void addRelationship_validator_예외발생시_예외_전파() {
        RelationshipProto.AddRelationshipRequest request = RelationshipProto.AddRelationshipRequest.newBuilder()
                .setName("")
                .setTag("00001")
                .build();
        doThrow(new IllegalArgumentException("name must not be blank"))
                .when(relationValidator).validateAddRelationshipRequest(request);

        assertThatThrownBy(() -> relationGrpc.addRelationship(request, responseObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }

    @Test
    void addRelationship_정상_요청이면_onNext와_onCompleted_호출() {
        RelationshipProto.AddRelationshipRequest request = RelationshipProto.AddRelationshipRequest.newBuilder()
                .setName("Bob")
                .setTag("00002")
                .build();
        // Service 가 이미 presigned URL 적용된 user 를 가진 Relation 을 반환한다고 가정
        User user = new User(2L, "b@b.com", "Bob", "00002", null, "id2",
                OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber());
        user.setProfileImageUrl("https://signed.example.com/profiles/2/img?sig=xyz");
        List<Relation> relations = List.of(new Relation(1L, user, RelationStatus.PENDING.forNumber()));

        when(relationService.addRelationship(1L, "Bob", "00002")).thenReturn(relations);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> relationGrpc.addRelationship(request, responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getRelationshipsCount() == 1 &&
                        response.getRelationships(0).getUser().getProfileImageUrl()
                                .equals("https://signed.example.com/profiles/2/img?sig=xyz")
        ));
        verify(responseObserver).onCompleted();
    }
}
