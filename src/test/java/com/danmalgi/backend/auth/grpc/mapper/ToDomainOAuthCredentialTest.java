package com.danmalgi.backend.auth.grpc.mapper;

import com.danmalgi.backend.auth.domain.model.OAuthCredential;
import com.danmalgi.backend.external.auth.v1.AuthProto;
import com.danmalgi.backend.external.user.v1.UserProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToDomainOAuthCredentialTest {

    @Test
    void toDomainOAuthCredential_null이면_예외발생() {
        assertThatThrownBy(() -> AuthGrpcMapper.toDomainOAuthCredential(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("request must not be null");
    }

    @Test
    void toDomainOAuthCredential_idToken과_rawNonce를_그대로_옮긴다() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("apple-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.APPLE)
                .setRawNonce("raw-nonce")
                .build();

        OAuthCredential result = AuthGrpcMapper.toDomainOAuthCredential(request);

        assertThat(result).isEqualTo(new OAuthCredential("apple-token", "raw-nonce", null));
    }

    @Test
    void toDomainOAuthCredential_authorizationCode를_그대로_옮긴다() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("apple-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.APPLE)
                .setAuthorizationCode("auth-code")
                .build();

        assertThat(AuthGrpcMapper.toDomainOAuthCredential(request).authorizationCode()).isEqualTo("auth-code");
    }

    @Test
    void toDomainOAuthCredential_authorizationCode를_보내지_않으면_null로_변환() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("google-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.GOOGLE)
                .build();

        assertThat(AuthGrpcMapper.toDomainOAuthCredential(request).authorizationCode()).isNull();
    }

    @Test
    void toDomainOAuthCredential_rawNonce를_보내지_않으면_null로_변환() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("google-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.GOOGLE)
                .build();

        assertThat(AuthGrpcMapper.toDomainOAuthCredential(request).rawNonce()).isNull();
    }

    @Test
    void toDomainOAuthCredential_rawNonce가_공백뿐이면_null로_변환() {
        AuthProto.AuthorizationRequest request = AuthProto.AuthorizationRequest.newBuilder()
                .setIdToken("apple-token")
                .setDeviceId("device-1")
                .setOauthType(UserProto.OauthType.APPLE)
                .setRawNonce("   ")
                .build();

        assertThat(AuthGrpcMapper.toDomainOAuthCredential(request).rawNonce()).isNull();
    }
}
