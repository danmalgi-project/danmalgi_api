package com.danmalgi.backend.relation.grpc;

import com.danmalgi.backend.external.relationship.v1.RelationshipProto;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.relation.domain.model.RelationStatus;
import com.danmalgi.backend.relation.grpc.validator.RelationGrpcValidator;
import com.danmalgi.backend.relation.service.RelationService;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationGrpcUpdateIncomingRelationshipStatusTest {

    @Mock
    private RelationGrpcValidator relationValidator;

    @Mock
    private RelationService relationService;

    @Mock
    private StreamObserver<RelationshipProto.UpdateIncomingRelationshipStatusResponse> responseObserver;

    private RelationGrpc relationGrpc;

    @BeforeEach
    void setUp() {
        relationGrpc = new RelationGrpc(relationValidator, relationService);
    }

    @Test
    void updateIncomingRelationshipStatus_validator_예외발생시_예외_전파() {
        RelationshipProto.UpdateIncomingRelationshipStatusRequest request =
                RelationshipProto.UpdateIncomingRelationshipStatusRequest.newBuilder()
                        .setRelationshipId(0L)
                        .setRelationshipStatus(RelationshipProto.RelationshipStatus.RELATION_ACCEPT)
                        .build();
        doThrow(new IllegalArgumentException("relationshipId must be positive"))
                .when(relationValidator).validateUpdateRelationshipStatusRequest(0L, RelationshipProto.RelationshipStatus.RELATION_ACCEPT);

        assertThatThrownBy(() -> relationGrpc.updateIncomingRelationshipStatus(request, responseObserver))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateIncomingRelationshipStatus_정상_요청이면_onNext와_onCompleted_호출() {
        RelationshipProto.UpdateIncomingRelationshipStatusRequest request =
                RelationshipProto.UpdateIncomingRelationshipStatusRequest.newBuilder()
                        .setRelationshipId(1L)
                        .setRelationshipStatus(RelationshipProto.RelationshipStatus.RELATION_ACCEPT)
                        .build();

        when(relationService.updateIncomingRequest(2L, 1L, RelationStatus.ACCEPT.forNumber()))
                .thenReturn(List.of());

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 2L);
        ctx.run(() -> relationGrpc.updateIncomingRelationshipStatus(request, responseObserver));

        verify(responseObserver).onNext(any());
        verify(responseObserver).onCompleted();
    }
}
