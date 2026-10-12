package com.danmalgi.backend.user.grpc.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.external.user.v1.UserProto;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.fixture.UserFixture;

class UserGrpcMapperToSearchUserResponseTest {

    @Test
    void toSearchUserResponse_유저가_있으면_id_name_tag_profileImageUrl이_매핑된다() {
        String expectedUrl = "https://cdn.example.com/profiles/2/abc.webp";
        User user = UserFixture.createUser(2L, "홍길동", "0001");
        user.setProfileImageUrl(expectedUrl);

        UserProto.SearchUserResponse response = UserGrpcMapper.toSearchUserResponse(List.of(user));

        assertThat(response.getUsersList()).singleElement().satisfies(searched -> {
            assertThat(searched.getId()).isEqualTo(2L);
            assertThat(searched.getName()).isEqualTo("홍길동");
            assertThat(searched.getTag()).isEqualTo("0001");
            assertThat(searched.getProfileImageUrl()).isEqualTo(expectedUrl);
        });
    }

    @Test
    void toSearchUserResponse_빈리스트면_users가_비어있다() {
        UserProto.SearchUserResponse response = UserGrpcMapper.toSearchUserResponse(List.of());

        assertThat(response.getUsersList()).isEmpty();
    }

    @Test
    void toSearchUserResponse_profileImageUrl이_null이면_빈문자열로_매핑된다() {
        User user = UserFixture.createUser(2L, "홍길동", "0001");

        UserProto.SearchUserResponse response = UserGrpcMapper.toSearchUserResponse(List.of(user));

        assertThat(response.getUsers(0).getProfileImageUrl()).isEmpty();
    }
}
