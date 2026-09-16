package com.danmalgi.backend.store.user_store.grpc.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.internal.user_store.v1.UserStoreProto;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import com.danmalgi.backend.user.fixture.UserFixture;

class UserStoreGrpcMapperToProtoUserTest {

    @Test
    void toProtoUser_id_email_name_tag가_매핑된다() {
        // given
        User user = UserFixture.createUser(1L, "testUser", "tag1");

        // when
        UserStoreProto.User protoUser = UserStoreGrpcMapper.toProtoUser(user);

        // then
        assertThat(protoUser.getId()).isEqualTo(1L);
        assertThat(protoUser.getEmail()).isEqualTo("test@test.com");
        assertThat(protoUser.getName()).isEqualTo("testUser");
        assertThat(protoUser.getTag()).isEqualTo("tag1");
    }

    @Test
    void toProtoUser_oauthType이_매핑된다() {
        // given
        User user = UserFixture.createUser(1L, "testUser", "tag1");

        // when
        UserStoreProto.User protoUser = UserStoreGrpcMapper.toProtoUser(user);

        // then
        assertThat(protoUser.getOauthType()).isEqualTo(OauthType.KAKAO.getNumber());
    }

    @Test
    void toProtoUser_status가_매핑된다() {
        // given
        User user = UserFixture.createUser(1L, "testUser", "tag1");

        // when
        UserStoreProto.User protoUser = UserStoreGrpcMapper.toProtoUser(user);

        // then
        assertThat(protoUser.getStatus()).isEqualTo(UserStatus.ACTIVE.getNumber());
    }

    @Test
    void toProtoUser_profileImageUrl이_있으면_매핑된다() {
        // given
        String expectedUrl = "https://r2.example.com/danmalgi/profiles/1/uuid";
        User user = UserFixture.createUser(1L, "testUser", "tag1");
        user.setProfileImageUrl(expectedUrl);

        // when
        UserStoreProto.User protoUser = UserStoreGrpcMapper.toProtoUser(user);

        // then
        assertThat(protoUser.getProfileImageUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void toProtoUser_profileImageUrl이_null이면_예외없이_빈문자열로_매핑된다() {
        // given
        User user = UserFixture.createUser(1L, "testUser", "tag1");
        // profileImageUrl 미설정 → null (프로필 미등록 사용자)

        // when & then
        assertThatCode(() -> UserStoreGrpcMapper.toProtoUser(user)).doesNotThrowAnyException();
        assertThat(UserStoreGrpcMapper.toProtoUser(user).getProfileImageUrl()).isEmpty();
    }

    @Test
    void toProtoUser_정의된_enum_범위_밖_값도_그대로_매핑된다() {
        // given — internal proto 는 int32 이므로 검증 없이 통과시키는 것이 확정된 설계다
        User user = new User(1L, "test@test.com", "testUser", "tag1", null, "oauth-id-1", 99, 99);

        // when
        UserStoreProto.User protoUser = UserStoreGrpcMapper.toProtoUser(user);

        // then
        assertThat(protoUser.getOauthType()).isEqualTo(99);
        assertThat(protoUser.getStatus()).isEqualTo(99);
    }
}
