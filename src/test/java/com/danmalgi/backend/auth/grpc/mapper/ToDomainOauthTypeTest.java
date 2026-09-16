package com.danmalgi.backend.auth.grpc.mapper;

import com.danmalgi.backend.external.user.v1.UserProto;
import com.danmalgi.backend.user.domain.model.OauthType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToDomainOauthTypeTest {

    @Test
    void toDomainOauthType_null이면_예외발생() {
        assertThatThrownBy(() -> AuthGrpcMapper.toDomainOauthType(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("oauthType is invalid");
    }

    @Test
    void toDomainOauthType_UNRECOGNIZED이면_예외발생() {
        assertThatThrownBy(() -> AuthGrpcMapper.toDomainOauthType(UserProto.OauthType.UNRECOGNIZED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("oauthType is invalid");
    }

    @Test
    void toDomainOauthType_NAVER를_도메인_NAVER로_변환() {
        OauthType result = AuthGrpcMapper.toDomainOauthType(UserProto.OauthType.NAVER);

        assertThat(result).isEqualTo(OauthType.NAVER);
    }

    @Test
    void toDomainOauthType_GOOGLE을_도메인_GOOGLE로_변환() {
        OauthType result = AuthGrpcMapper.toDomainOauthType(UserProto.OauthType.GOOGLE);

        assertThat(result).isEqualTo(OauthType.GOOGLE);
    }

    @Test
    void toDomainOauthType_KAKAO를_도메인_KAKAO로_변환() {
        OauthType result = AuthGrpcMapper.toDomainOauthType(UserProto.OauthType.KAKAO);

        assertThat(result).isEqualTo(OauthType.KAKAO);
    }
}
