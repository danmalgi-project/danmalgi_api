package com.danmalgi.backend.user.grpc.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.external.user.v1.UserProto;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.fixture.UserFixture;

class UserGrpcMapperToUploadProfileResponseTest {

    @Test
    void toUploadProfileResponse_user가_올바르게_매핑된다() {
        // given
        String expectedUrl = "https://r2.example.com/danmalgi/profiles/1/uuid";
        User user = UserFixture.createUser(1L, "testUser", "tag1");
        user.setProfileImageUrl(expectedUrl);

        // when
        UserProto.UploadProfileResponse response = UserGrpcMapper.toUploadProfileResponse(user);

        // then
        UserProto.User protoUser = response.getUser();
        assertThat(protoUser.getId()).isEqualTo(1L);
        assertThat(protoUser.getEmail()).isEqualTo("test@test.com");
        assertThat(protoUser.getName()).isEqualTo("testUser");
        assertThat(protoUser.getTag()).isEqualTo("tag1");
        assertThat(protoUser.getProfileImageUrl()).isEqualTo(expectedUrl);
        assertThat(protoUser.getOauthType()).isEqualTo(UserProto.OauthType.KAKAO);
        assertThat(protoUser.getStatus()).isEqualTo(UserProto.UserStatus.USER_ACTIVE);
    }

    @Test
    void toUploadProfileResponse_profileImageUrl이_null이면_빈문자열로_매핑된다() {
        // given
        User user = UserFixture.createUser(1L, "testUser", "tag1");

        // when
        UserProto.UploadProfileResponse response = UserGrpcMapper.toUploadProfileResponse(user);

        // then
        assertThat(response.getUser().getProfileImageUrl()).isEmpty();
    }
}
