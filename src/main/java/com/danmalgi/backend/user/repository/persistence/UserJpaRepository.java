package com.danmalgi.backend.user.repository.persistence;

import com.danmalgi.backend.user.repository.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByOauthTypeAndIdentifyId(int oauthType, String identifyId);
    Optional<UserEntity> findByNameAndTag(String name, String tag);

    /**
     * {@code users.id} 시퀀스에서 다음 값을 미리 뽑는다.
     *
     * <p>Authorization 단계에서 행을 INSERT 하지 않고 userId 를 확보하기 위한 것이다.
     * PostgreSQL 시퀀스는 롤백되지 않으므로 회원가입 중도 이탈은 gap 만 남긴다 (의도).
     *
     * <p>컬럼에 시퀀스가 없으면 {@code pg_get_serial_sequence} 가 NULL 을 반환해
     * 결과도 NULL 이다 — 호출부가 확인한다.
     */
    @Query(value = "SELECT nextval(pg_get_serial_sequence('users', 'id'))", nativeQuery = true)
    Long nextUserId();
}
