package com.danmalgi.backend.user.repository.entity;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.user.domain.model.OauthType;

import jakarta.persistence.PostPersist;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INSERT 직후 {@code isNew} 를 되돌리는 콜백을 고정한다.
 *
 * <p>되돌리지 않으면 같은 인스턴스를 두 번 {@code save()} 할 때 이미 INSERT 된 행에
 * 다시 {@code persist} 를 시도해 PK 충돌이 난다.
 *
 * <p>실제 Hibernate 라이프사이클 호출은 영속성 컨텍스트가 필요해 단위 테스트로 검증할 수 없다.
 * 그래서 (1) 콜백 본문의 동작과 (2) {@code @PostPersist} 선언 여부를 나눠 검증한다.
 */
class UserEntityMarkNotNewTest {

    private UserEntity newEntity() {
        return UserEntity.registerNew(
                100L, "test@gmail.com", "홍길동", "00001", "google-sub-123",
                OauthType.GOOGLE.getNumber(), null);
    }

    @Test
    void markNotNew_호출하면_isNew가_false로_되돌아간다() {
        UserEntity entity = newEntity();

        entity.markNotNew();

        assertThat(entity.isNew()).isFalse();
    }

    @Test
    void markNotNew에_PostPersist가_선언되어_있다() throws NoSuchMethodException {
        // 선언이 없으면 Hibernate 가 콜백을 호출하지 않아 isNew 가 true 로 남는다.
        Method markNotNew = UserEntity.class.getDeclaredMethod("markNotNew");

        assertThat(markNotNew.getAnnotation(PostPersist.class)).isNotNull();
    }
}
