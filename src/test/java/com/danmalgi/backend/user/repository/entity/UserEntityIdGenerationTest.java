package com.danmalgi.backend.user.repository.entity;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityIdGenerationTest {

    @Test
    void id는_생성전략_없이_할당되는_PK다() throws Exception {
        Field id = UserEntity.class.getDeclaredField("id");

        assertThat(id.getAnnotation(Id.class)).isNotNull();
        // @GeneratedValue 가 남아 있으면 Hibernate 가 assigned id 를 버리고 DB 가 만든 id 를
        // 쓴다. Authorization 에서 확보한 id 로 발급한 JWT 의 userId 클레임이 실제 행과
        // 어긋나 회원가입 직후 모든 인증이 깨진다.
        assertThat(id.getAnnotation(GeneratedValue.class)).isNull();
    }
}
