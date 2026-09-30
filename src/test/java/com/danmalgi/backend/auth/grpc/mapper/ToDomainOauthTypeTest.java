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
    void toDomainOauthType_GOOGLE을_도메인_GOOGLE로_변환() {
        OauthType result = AuthGrpcMapper.toDomainOauthType(UserProto.OauthType.GOOGLE);

        assertThat(result).isEqualTo(OauthType.GOOGLE);
    }

    @Test
    void toDomainOauthType_APPLE을_도메인_APPLE로_변환() {
        OauthType result = AuthGrpcMapper.toDomainOauthType(UserProto.OauthType.APPLE);

        assertThat(result).isEqualTo(OauthType.APPLE);
    }

    @Test
    void 도메인_number는_proto_wire_값과_같다() {
        // users.oauth_type 저장값 == proto wire 값 전제를 고정한다 (chat 서버가 int 를 그대로 캐스팅).
        for (OauthType oauthType : OauthType.values()) {
            assertThat(UserProto.OauthType.forNumber(oauthType.getNumber()).name())
                    .isEqualTo(oauthType.name());
        }
    }
}
