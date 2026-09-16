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
class RelationGrpcUpdateOutgoingRelationshipStatusTest {

    @Mock
    private RelationGrpcValidator relationValidator;

    @Mock
    private RelationService relationService;

    @Mock
    private StreamObserver<RelationshipProto.UpdateOutgoingRelationshipStatusResponse> responseObserver;

    private RelationGrpc relationGrpc;

    @BeforeEach
    void setUp() {
        relationGrpc = new RelationGrpc(relationValidator, relationService);
    }

    @Test
    void updateOutgoingRelationshipStatus_validator_예외발생시_예외_전파() {
        RelationshipProto.UpdateOutgoingRelationshipStatusRequest request =
                RelationshipProto.UpdateOutgoingRelationshipStatusRequest.newBuilder()
                        .setRelationshipId(0L)
                        .setRelationshipStatus(RelationshipProto.RelationshipStatus.RELATION_CANCEL)
                        .build();
        doThrow(new IllegalArgumentException("relationshipId must be positive"))
                .when(relationValidator).validateUpdateRelationshipStatusRequest(0L, RelationshipProto.RelationshipStatus.RELATION_CANCEL);

        assertThatThrownBy(() -> relationGrpc.updateOutgoingRelationshipStatus(request, responseObserver))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateOutgoingRelationshipStatus_정상_요청이면_onNext와_onCompleted_호출() {
        RelationshipProto.UpdateOutgoingRelationshipStatusRequest request =
                RelationshipProto.UpdateOutgoingRelationshipStatusRequest.newBuilder()
                        .setRelationshipId(1L)
                        .setRelationshipStatus(RelationshipProto.RelationshipStatus.RELATION_CANCEL)
                        .build();

        when(relationService.updateOutgoingRequest(1L, 1L, RelationStatus.CANCEL.forNumber()))
                .thenReturn(List.of());

        Context ctx = Context.current().withValue(GrpcContext.USER_ID, 1L);
        ctx.run(() -> relationGrpc.updateOutgoingRelationshipStatus(request, responseObserver));

        verify(responseObserver).onNext(any());
        verify(responseObserver).onCompleted();
    }
}
