package com.danmalgi.backend.auth.grpc;

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.grpc.validator.AuthGrpcValidator;
import com.danmalgi.backend.auth.grpc.dto.AuthorizeResponse;
import com.danmalgi.backend.auth.service.AuthService;
import com.danmalgi.backend.external.auth.v1.AuthProto;
import com.danmalgi.backend.external.user.v1.UserProto;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthGrpcAuthorizationTest {

    @Mock
    private AuthGrpcValidator authGrpcValidator;

    @Mock
    private AuthService authService;

    @Mock
    private StreamObserver<AuthProto.AuthorizationResponse> responseObserver;

    private AuthGrpc authGrpc;

    @BeforeEach
    void setUp() {
        authGrpc = new AuthGrpc(authGrpcValidator, authService);
    }

    @Test
    void authorization_validator_예외발생시_예외_전파() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.GOOGLE)
                .build();
        doThrow(new IllegalArgumentException("idToken must not be blank"))
                .when(authGrpcValidator).validateAuthorizationRequest(request);

        assertThatThrownBy(() -> authGrpc.authorization(request, responseObserver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idToken must not be blank");
    }

    @Test
    void authorization_정상_요청이면_onNext와_onCompleted_호출() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("valid-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.GOOGLE)
                .build();
        // service 가 이미 presigned URL 로 변환된 user 를 반환한다고 가정
        User user = new User(1L, "test@gmail.com", "TestUser", "12345", "device-1",
                "google-sub", OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber());
        user.setProfileImageUrl("https://signed.example.com/profiles/1/img?sig=abc");
        when(authService.authorize("valid-token", "device-1", OauthType.GOOGLE))
                .thenReturn(AuthorizeResponse.ofRegisteredUser(user, "jwt-token"));

        authGrpc.authorization(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getAccessToken().equals("jwt-token")
                        && response.getUser().getProfileImageUrl()
                                .equals("https://signed.example.com/profiles/1/img?sig=abc")
                        // 등록완료 응답은 DB 의 name/tag 와 USER_ACTIVE 로 나간다 (이슈 #25 동반 해소).
                        && response.getUser().getStatus() == UserProto.UserStatus.USER_ACTIVE
                        && response.getUser().getName().equals("TestUser")
                        && response.getUser().getTag().equals("12345")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void authorization_pending_응답이면_status가_USER_PENDING이고_name과_tag가_비어있다() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("valid-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.GOOGLE)
                .build();
        PendingOAuthProfile profile = new PendingOAuthProfile(
                100L, "test@gmail.com", "google-sub", OauthType.GOOGLE.getNumber(), null);
        when(authService.authorize("valid-token", "device-1", OauthType.GOOGLE))
                .thenReturn(AuthorizeResponse.ofPendingProfile(profile, "jwt-token"));

        authGrpc.authorization(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getUser().getStatus() == UserProto.UserStatus.USER_PENDING
                        && response.getUser().getId() == 100L
                        && response.getUser().getName().isEmpty()
                        && response.getUser().getTag().isEmpty()
                        && response.getAccessToken().equals("jwt-token")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void authorization_jwtToken이_null이면_accessToken은_빈_문자열() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("valid-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.GOOGLE)
                .build();
        User user = new User(1L, "test@gmail.com", "TestUser", "12345", "device-1",
                "google-sub", OauthType.GOOGLE.getNumber(), UserStatus.PENDING.getNumber());
        when(authService.authorize(any(), any(), any()))
                .thenReturn(AuthorizeResponse.ofRegisteredUser(user, null));

        authGrpc.authorization(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getAccessToken().isEmpty()
        ));
        verify(responseObserver).onCompleted();
    }
}
