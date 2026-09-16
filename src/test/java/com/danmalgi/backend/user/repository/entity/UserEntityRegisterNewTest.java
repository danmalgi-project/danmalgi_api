package com.danmalgi.backend.user.repository.entity;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원가입 완료 시점의 최초 INSERT 용 팩토리가 인자를 그대로 채우는지 고정한다.
 *
 * <p>{@code id} 는 Authorization 단계에서 시퀀스로 확보한 값이므로 그대로 유지돼야 한다.
 */
class UserEntityRegisterNewTest {

    @Test
    void registerNew_인자를_그대로_채우고_status는_ACTIVE다() {
        UserEntity entity = UserEntity.registerNew(
                100L, "test@gmail.com", "홍길동", "00001", "google-sub-123",
                OauthType.GOOGLE.getNumber(), "profiles/100/a.webp");

        assertThat(entity.getId()).isEqualTo(100L);
        assertThat(entity.getEmail()).isEqualTo("test@gmail.com");
        assertThat(entity.getName()).isEqualTo("홍길동");
        assertThat(entity.getTag()).isEqualTo("00001");
        assertThat(entity.getIdentifyId()).isEqualTo("google-sub-123");
        assertThat(entity.getOauthType()).isEqualTo(OauthType.GOOGLE.getNumber());
        assertThat(entity.getProfileImageUrl()).isEqualTo("profiles/100/a.webp");
        assertThat(entity.getStatus()).isEqualTo(UserStatus.ACTIVE.getNumber());
    }

    @Test
    void registerNew_profileImageUrl이_null이면_null로_유지된다() {
        // Authorization 경로에서 profileImageUrl 은 항상 null 이다 (어댑터가 picture 클레임을 읽지 않는다).
        UserEntity entity = UserEntity.registerNew(
                100L, "test@gmail.com", "홍길동", "00001", "google-sub-123",
                OauthType.GOOGLE.getNumber(), null);

        assertThat(entity.getProfileImageUrl()).isNull();
    }

    @Test
    void registerNew_toDomainUser로_왕복해도_값이_유지된다() {
        UserEntity entity = UserEntity.registerNew(
                100L, "test@gmail.com", "홍길동", "00001", "google-sub-123",
                OauthType.GOOGLE.getNumber(), null);

        assertThat(entity.toDomainUser().getId()).isEqualTo(100L);
        assertThat(entity.toDomainUser().getStatus()).isEqualTo(UserStatus.ACTIVE.getNumber());
    }
}
