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
import com.google.protobuf.Empty;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationGrpcGetOutgoingRelationshipListTest {

    @Mock
    private RelationGrpcValidator relationValidator;

    @Mock
    private RelationService relationService;

    @Mock
    private StreamObserver<RelationshipProto.GetOutgoingRelationshipListResponse> responseObserver;

    private RelationGrpc relationGrpc;

    @BeforeEach
    void setUp() {
        relationGrpc = new RelationGrpc(relationValidator, relationService);
    }

    @Test
    void getOutgoingRelationshipList_정상_요청이면_onNext와_onCompleted_호출() {
        // Service 가 이미 presigned URL 적용된 user 를 가진 Relation 을 반환한다고 가정
        User user = new User(2L, "b@b.com", "Bob", "00002", null, "id2",
                OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber());
        user.setProfileImageUrl("https://signed.example.com/profiles/2/img?sig=xyz");
        List<Relation> relations = List.of(new Relation(1L, user, RelationStatus.PENDING.forNumber()));

        when(relationService.getOutgoingRequest(1L)).thenReturn(relations);

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> relationGrpc.getOutgoingRelationshipList(Empty.getDefaultInstance(), responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getRelationshipsCount() == 1 &&
                        response.getRelationships(0).getUser().getProfileImageUrl()
                                .equals("https://signed.example.com/profiles/2/img?sig=xyz")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void getOutgoingRelationshipList_결과가_없으면_빈_목록으로_응답() {
        when(relationService.getOutgoingRequest(1L)).thenReturn(List.of());

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> relationGrpc.getOutgoingRelationshipList(Empty.getDefaultInstance(), responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getRelationshipsCount() == 0
        ));
        verify(responseObserver).onCompleted();
    }
}
