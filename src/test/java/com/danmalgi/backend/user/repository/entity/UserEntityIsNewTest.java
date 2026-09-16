package com.danmalgi.backend.user.repository.entity;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Persistable;

import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code isNew()} 가 4개 생성 경로에서 각각 어떤 값을 돌려주는지 고정한다.
 *
 * <p>이 값이 {@code save()} 의 분기를 결정한다. {@code false} 면 {@code merge},
 * {@code true} 면 {@code persist} 다. merge 는 사전 확보한 id 가 기존 행과 충돌할 때
 * INSERT 대신 조용히 UPDATE 해 남의 유저 행을 덮어쓴다.
 */
class UserEntityIsNewTest {

    private User user(Long id) {
        return new User(id, "test@gmail.com", "홍길동", "00001", null,
                "google-sub-123", OauthType.GOOGLE.getNumber(), UserStatus.ACTIVE.getNumber());
    }

    @Test
    void UserEntity는_Persistable을_구현한다() {
        // 이 구현이 없으면 JpaMetamodelEntityInformation 이 id != null 만 보고 merge 로 보낸다.
        // isNew() 메서드만 남기고 implements 를 지우면 Spring Data 는 이 메서드를 보지 않는다.
        assertThat(Persistable.class).isAssignableFrom(UserEntity.class);
    }

    @Test
    void 조회된_엔티티는_isNew가_false다() {
        // Hibernate 가 조회 시 쓰는 no-arg 생성자 경로. 이미 DB 에 있는 행이므로 merge 여야 한다.
        UserEntity entity = new UserEntity();

        assertThat(entity.isNew()).isFalse();
    }

    @Test
    void from에_id가_있으면_isNew가_false다() {
        assertThat(UserEntity.from(user(1L)).isNew()).isFalse();
    }

    @Test
    void from에_id가_없으면_isNew가_true다() {
        // 오늘의 판정(id == null → persist)을 그대로 복제해야 회귀가 없다.
        assertThat(UserEntity.from(user(null)).isNew()).isTrue();
    }

    @Test
    void registerNew로_만든_엔티티는_isNew가_true다() {
        // isNew=true 여야 save() 가 merge 가 아니라 persist 를 타
        // 사전 확보한 id 의 PK 충돌이 제약 위반으로 드러난다.
        UserEntity entity = UserEntity.registerNew(
                100L, "test@gmail.com", "홍길동", "00001", "google-sub-123",
                OauthType.GOOGLE.getNumber(), null);

        assertThat(entity.isNew()).isTrue();
    }
}
