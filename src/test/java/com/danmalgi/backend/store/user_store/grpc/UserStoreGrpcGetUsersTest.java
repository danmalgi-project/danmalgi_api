package com.danmalgi.backend.store.user_store.grpc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danmalgi.backend.internal.user_store.v1.UserStoreProto;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.fixture.UserFixture;
import com.danmalgi.backend.user.service.UserService;

import io.grpc.stub.StreamObserver;

@ExtendWith(MockitoExtension.class)
class UserStoreGrpcGetUsersTest {

    @Mock
    private UserService userService;

    @Mock
    private StreamObserver<UserStoreProto.GetUsersResponse> responseObserver;

    private UserStoreGrpc userStoreGrpc;

    @BeforeEach
    void setUp() {
        userStoreGrpc = new UserStoreGrpc(userService);
    }

    @Test
    void getUsers_UserService_getUsers를_호출한다() {
        // given
        UserStoreProto.GetUsersRequest request = UserStoreProto.GetUsersRequest.newBuilder()
                .addAllUserIds(List.of(1L))
                .build();
        when(userService.getUsers(List.of(1L))).thenReturn(List.of());

        // when
        userStoreGrpc.getUsers(request, responseObserver);

        // then
        verify(userService).getUsers(List.of(1L));
    }

    @Test
    void getUsers_profileImageUrl에_presigned_URL이_그대로_응답에_실린다() {
        // given
        String presignedUrl = "https://r2.example.com/danmalgi/profiles/1/uuid?sig=xyz";
        User user = UserFixture.createUser(1L, "testUser", "tag1");
        user.setProfileImageUrl(presignedUrl);

        UserStoreProto.GetUsersRequest request = UserStoreProto.GetUsersRequest.newBuilder()
                .addAllUserIds(List.of(1L))
                .build();
        when(userService.getUsers(List.of(1L))).thenReturn(List.of(user));

        // when
        userStoreGrpc.getUsers(request, responseObserver);

        // then
        verify(responseObserver).onNext(argThat(response ->
                response.getUsersCount() == 1
                        && response.getUsers(0).getProfileImageUrl().equals(presignedUrl)
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void getUsers_요청의_user_ids가_Service로_그대로_전달된다() {
        // given
        UserStoreProto.GetUsersRequest request = UserStoreProto.GetUsersRequest.newBuilder()
                .addAllUserIds(List.of(10L, 20L, 30L))
                .build();
        when(userService.getUsers(List.of(10L, 20L, 30L))).thenReturn(List.of());

        // when
        userStoreGrpc.getUsers(request, responseObserver);

        // then
        verify(userService).getUsers(List.of(10L, 20L, 30L));
    }

    @Test
    void getUsers_결과가_없으면_빈_응답을_반환한다() {
        // given
        UserStoreProto.GetUsersRequest request = UserStoreProto.GetUsersRequest.newBuilder()
                .addAllUserIds(List.of(1L))
                .build();
        when(userService.getUsers(List.of(1L))).thenReturn(List.of());

        // when
        userStoreGrpc.getUsers(request, responseObserver);

        // then
        verify(responseObserver).onNext(argThat(response -> response.getUsersCount() == 0));
        verify(responseObserver).onCompleted();
    }
}
