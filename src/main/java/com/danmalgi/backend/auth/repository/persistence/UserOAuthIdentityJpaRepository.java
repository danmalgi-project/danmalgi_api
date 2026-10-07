package com.danmalgi.backend.auth.repository.persistence;

import com.danmalgi.backend.auth.repository.entity.UserOAuthIdentityEntity;
import com.danmalgi.backend.user.domain.model.OauthType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserOAuthIdentityJpaRepository extends JpaRepository<UserOAuthIdentityEntity, Long> {

    /**
     * 로그인 조회. {@code user} 를 함께 읽는다.
     *
     * <p>Authorization 은 트랜잭션 밖이고 gRPC 에는 OSIV 가 없어서, LAZY 인 user 를 나중에
     * 건드리면 {@code LazyInitializationException} 이 난다.
     */
    @EntityGraph(attributePaths = "user")
    Optional<UserOAuthIdentityEntity> findByProviderAndProviderSubject(OauthType provider, String providerSubject);

    /**
     * 기존 유저 로그인 시 마지막 인증 시각과 제공자 이메일을 갱신한다.
     *
     * <p>{@code @Transactional} 은 트랜잭션 밖(Authorization)에서 부르기 때문이다.
     */
    @Transactional
    @Modifying
    @Query("update UserOAuthIdentityEntity i set i.email = :email, i.lastAuthenticatedAt = :authenticatedAt where i.id = :id")
    int touchAuthenticated(
            @Param("id") Long id,
            @Param("email") String email,
            @Param("authenticatedAt") Instant authenticatedAt
    );
}
