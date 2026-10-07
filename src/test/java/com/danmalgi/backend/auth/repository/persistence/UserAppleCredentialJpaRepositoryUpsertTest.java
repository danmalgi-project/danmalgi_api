package com.danmalgi.backend.auth.repository.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 네이티브 쿼리라 선언만 검증한다. 실제 ON CONFLICT 동작은 PostgreSQL 에서 확인해야 한다.
 */
class UserAppleCredentialJpaRepositoryUpsertTest {

    private Query upsertQuery() throws NoSuchMethodException {
        Method upsert = UserAppleCredentialJpaRepository.class
                .getDeclaredMethod("upsert", Long.class, String.class, String.class);
        return upsert.getAnnotation(Query.class);
    }

    @Test
    void upsert는_신원_id를_키로_충돌을_처리한다() throws Exception {
        // 토큰은 Apple 계정(신원)에 속한다. user_id 로 두면 연동 해제 때 어느 토큰인지 구분할 수 없다.
        Query query = upsertQuery();

        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).contains("ON CONFLICT (identity_id)");
        assertThat(query.value()).doesNotContain("user_id");
    }
}
