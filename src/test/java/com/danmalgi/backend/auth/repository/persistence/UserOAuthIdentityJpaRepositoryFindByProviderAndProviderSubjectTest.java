package com.danmalgi.backend.auth.repository.persistence;

import com.danmalgi.backend.user.domain.model.OauthType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선언만 검증한다. 실제 조회는 PostgreSQL 없이 확인할 수 없다 (UserJpaRepositoryNextUserIdTest 와 같은 이유).
 */
class UserOAuthIdentityJpaRepositoryFindByProviderAndProviderSubjectTest {

    @Test
    void findByProviderAndProviderSubject는_user를_함께_읽는다() throws Exception {
        // Authorization 은 트랜잭션 밖이고 OSIV 가 없어, user 를 지연 로딩하면 LazyInitializationException 이 난다.
        Method method = UserOAuthIdentityJpaRepository.class
                .getDeclaredMethod("findByProviderAndProviderSubject", OauthType.class, String.class);

        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);

        assertThat(entityGraph).isNotNull();
        assertThat(entityGraph.attributePaths()).containsExactly("user");
    }
}
