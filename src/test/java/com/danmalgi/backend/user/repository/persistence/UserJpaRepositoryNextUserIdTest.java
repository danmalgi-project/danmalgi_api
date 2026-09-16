package com.danmalgi.backend.user.repository.persistence;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code nextUserId()} 의 선언 형태를 고정한다.
 *
 * <p>네이티브 쿼리라 SQL 실행 자체는 PostgreSQL 없이는 검증할 수 없다 (dev DB 는 내려가 있고
 * 단위 테스트 원칙상 컨텍스트도 띄우지 않는다). 그래서 <b>선언</b>만 검증한다 —
 * 어노테이션이 빠지면 Spring Data 가 메서드명으로 쿼리를 파생시키려다 부팅에 실패하고,
 * 쿼리 문자열이 틀리면 사전 확보한 id 가 시퀀스와 어긋나 INSERT 시점에 PK 충돌이 난다.
 *
 * <p><b>검증 불가 항목</b>: 실제 {@code nextval} 이 시퀀스를 증가시키는지, 컬럼에 시퀀스가
 * 없을 때 NULL 이 오는지는 통합 테스트나 배포 전 수동 확인 대상이다.
 */
class UserJpaRepositoryNextUserIdTest {

    private Method nextUserId() throws NoSuchMethodException {
        return UserJpaRepository.class.getDeclaredMethod("nextUserId");
    }

    @Test
    void nextUserId_파라미터없이_Long을_반환한다() throws NoSuchMethodException {
        // 반환 타입이 long 이면 시퀀스가 없어 NULL 이 올 때 NPE 로 터진다. 호출부가 판단해야 한다.
        assertThat(nextUserId().getReturnType()).isEqualTo(Long.class);
        assertThat(nextUserId().getParameterCount()).isZero();
    }

    @Test
    void nextUserId에_nativeQuery인_Query가_선언되어_있다() throws NoSuchMethodException {
        Query query = nextUserId().getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
    }

    @Test
    void nextUserId_쿼리는_users_id_컬럼의_시퀀스에서_nextval을_읽는다() throws NoSuchMethodException {
        Query query = nextUserId().getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .containsIgnoringWhitespaces("nextval")
                .containsIgnoringWhitespaces("pg_get_serial_sequence('users', 'id')");
    }
}
