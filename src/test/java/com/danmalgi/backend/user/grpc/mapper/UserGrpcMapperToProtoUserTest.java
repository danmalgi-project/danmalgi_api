package com.danmalgi.backend.user.grpc.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.external.user.v1.UserProto;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.fixture.UserFixture;

class UserGrpcMapperToProtoUserTest {

    @Test
    void toProtoUser_profileImageUrl이_있으면_매핑된다() {
        // given
        String expectedUrl = "https://r2.example.com/danmalgi/profiles/1/uuid";
        User user = UserFixture.createUser(1L, "testUser", "tag1");
        user.setProfileImageUrl(expectedUrl);

        // when
        UserProto.User protoUser = UserGrpcMapper.toProtoUser(user);

        // then
        assertThat(protoUser.getProfileImageUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void toProtoUser_profileImageUrl이_null이면_빈문자열로_매핑된다() {
        // given
        User user = UserFixture.createUser(1L, "testUser", "tag1");
        // profileImageUrl not set → null

        // when
        UserProto.User protoUser = UserGrpcMapper.toProtoUser(user);

        // then
        assertThat(protoUser.getProfileImageUrl()).isEmpty();
    }
}
