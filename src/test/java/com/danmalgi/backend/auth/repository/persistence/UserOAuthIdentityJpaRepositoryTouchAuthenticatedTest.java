package com.danmalgi.backend.auth.repository.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserOAuthIdentityJpaRepositoryTouchAuthenticatedTest {

    private Method touchAuthenticated() throws NoSuchMethodException {
        return UserOAuthIdentityJpaRepository.class
                .getDeclaredMethod("touchAuthenticated", Long.class, String.class, Instant.class);
    }

    @Test
    void touchAuthenticated는_Modifying_쿼리다() throws Exception {
        // 없으면 Spring Data 가 UPDATE 를 SELECT 로 실행하려다 실패한다.
        assertThat(touchAuthenticated().getAnnotation(Modifying.class)).isNotNull();
    }

    @Test
    void touchAuthenticated는_자체_트랜잭션을_연다() throws Exception {
        // Authorization 은 트랜잭션 밖에서 부른다. 없으면 TransactionRequiredException 이 난다.
        assertThat(touchAuthenticated().getAnnotation(Transactional.class)).isNotNull();
    }
}
