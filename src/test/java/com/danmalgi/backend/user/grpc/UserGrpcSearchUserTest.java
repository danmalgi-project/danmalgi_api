package com.danmalgi.backend.user.grpc;

import static com.danmalgi.backend.user.fixture.UserFixture.createUser;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danmalgi.backend.external.user.v1.UserProto;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.user.grpc.validator.UserGrpcValidator;
import com.danmalgi.backend.user.service.UserService;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;

@ExtendWith(MockitoExtension.class)
class UserGrpcSearchUserTest {

    @Mock
    private UserGrpcValidator userGrpcValidator;

    @Mock
    private UserService userService;

    @Mock
    private StreamObserver<UserProto.SearchUserResponse> responseObserver;

    private UserGrpc userGrpc;

    @BeforeEach
    void setUp() {
        userGrpc = new UserGrpc(userGrpcValidator, userService);
    }

    @Test
    void searchUser_validator_예외발생시_예외_전파() {
        UserProto.SearchUserRequest request = UserProto.SearchUserRequest.newBuilder()
                .setName("")
                .setTag("0001")
                .build();
        doThrow(new IllegalArgumentException("name must not be blank"))
                .when(userGrpcValidator).validateSearchUserRequest(request);

        assertThatThrownBy(() -> userGrpc.searchUser(request, responseObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
        verifyNoInteractions(userService);
        verify(responseObserver, never()).onNext(any());
    }

    @Test
    void searchUser_정상요청이면_요청자_id로_조회해_onNext와_onCompleted_호출() {
        UserProto.SearchUserRequest request = UserProto.SearchUserRequest.newBuilder()
                .setName("홍길동")
                .setTag("0001")
                .build();
        when(userService.searchUser(1L, "홍길동", "0001"))
                .thenReturn(List.of(createUser(2L, "홍길동", "0001")));

        Context.current().withValue(GrpcContext.USER_ID, 1L)
                .run(() -> userGrpc.searchUser(request, responseObserver));

        verify(responseObserver).onNext(argThat(response ->
                response.getUsersCount() == 1 && response.getUsers(0).getId() == 2L));
        verify(responseObserver).onCompleted();
    }

    @Test
    void searchUser_결과가_없으면_빈_응답으로_onNext() {
        UserProto.SearchUserRequest request = UserProto.SearchUserRequest.newBuilder()
                .setName("없음")
                .setTag("9999")
                .build();
        when(userService.searchUser(1L, "없음", "9999")).thenReturn(List.of());

        Context.current().withValue(GrpcContext.USER_ID, 1L)
                .run(() -> userGrpc.searchUser(request, responseObserver));

        verify(responseObserver).onNext(argThat(response -> response.getUsersCount() == 0));
        verify(responseObserver).onCompleted();
    }
}
