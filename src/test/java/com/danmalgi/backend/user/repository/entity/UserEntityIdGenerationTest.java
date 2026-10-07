package com.danmalgi.backend.user.repository.entity;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
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

    @Test
    void id_컬럼은_시퀀스가_붙는_bigserial로_생성된다() throws Exception {
        // nextUserId() 가 pg_get_serial_sequence 로 이 시퀀스를 찾는다. 선언이 빠지면 DB 를 새로 만든
        // 순간 시퀀스 없는 bigint 가 되어 신규 가입이 전부 실패한다.
        Column column = UserEntity.class.getDeclaredField("id").getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.columnDefinition()).isEqualTo("bigserial");
    }
}
