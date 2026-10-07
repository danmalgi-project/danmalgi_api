package com.danmalgi.backend.auth.repository.persistence;

import com.danmalgi.backend.auth.repository.entity.UserAppleCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UserAppleCredentialJpaRepository extends JpaRepository<UserAppleCredentialEntity, Long> {

    /**
     * 같은 신원으로 동시에 로그인해도 한 문장으로 끝나도록 INSERT … ON CONFLICT 로 갱신한다.
     * find → save 로 하면 두 요청이 모두 "없음" 을 보고 INSERT 해 PK 충돌이 난다.
     *
     * <p>{@code @Transactional} 은 트랜잭션 밖(Authorization)에서 부를 때를 위한 것이다.
     * Register 처럼 바깥 트랜잭션이 있으면 거기 합류한다.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO user_apple_credentials (identity_id, client_id, refresh_token_encrypted, updated_at)
            VALUES (:identityId, :clientId, :refreshTokenEncrypted, now())
            ON CONFLICT (identity_id) DO UPDATE
               SET client_id = EXCLUDED.client_id,
                   refresh_token_encrypted = EXCLUDED.refresh_token_encrypted,
                   updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsert(
            @Param("identityId") Long identityId,
            @Param("clientId") String clientId,
            @Param("refreshTokenEncrypted") String refreshTokenEncrypted
    );
}
