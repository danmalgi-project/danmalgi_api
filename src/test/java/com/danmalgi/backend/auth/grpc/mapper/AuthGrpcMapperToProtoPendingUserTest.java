package com.danmalgi.backend.auth.grpc.mapper;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.external.user.v1.UserProto;
import com.danmalgi.backend.user.domain.model.OauthType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthGrpcMapperToProtoPendingUserTest {

    private PendingOAuthProfile profile() {
        return new PendingOAuthProfile(
                "test@gmail.com", "google-sub-123", OauthType.GOOGLE, null, null, null);
    }

    @Test
    void toProtoPendingUser_status는_USER_PENDING이고_name과_tag는_빈_문자열이다() {
        UserProto.User result = AuthGrpcMapper.toProtoPendingUser(profile());

        // 클라이언트는 status == USER_PENDING 으로 신규 가입을 판별한다.
        assertThat(result.getStatus()).isEqualTo(UserProto.UserStatus.USER_PENDING);
        assertThat(result.getName()).isEmpty();
        assertThat(result.getTag()).isEmpty();
        assertThat(result.getEmail()).isEqualTo("test@gmail.com");
    }

    @Test
    void toProtoPendingUser_null이면_IllegalArgumentException() {
        assertThatThrownBy(() -> AuthGrpcMapper.toProtoPendingUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pendingProfile");
    }

    @Test
    void toProtoPendingUser_id는_항상_0이다() {
        // pending 세션은 users.id 가 아니라 UUID 로 식별되고, int64 인 User.id 는 담을 수 없다.
        assertThat(AuthGrpcMapper.toProtoPendingUser(profile()).getId()).isZero();
    }

    @Test
    void toProtoPendingUser_profileImageUrl이_null이면_빈_문자열이다() {
        // Authorization 경로에서 항상 null 인 필드. R2 key 가 아니라 외부 URL 이므로
        // applyPublicProfileImageUrl 을 거치지 않는다.
        UserProto.User result = AuthGrpcMapper.toProtoPendingUser(profile());

        assertThat(result.getProfileImageUrl()).isEmpty();
    }
}
