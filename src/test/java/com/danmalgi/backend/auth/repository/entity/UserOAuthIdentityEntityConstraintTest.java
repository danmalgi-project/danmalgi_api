package com.danmalgi.backend.auth.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스키마 선언을 고정한다. ddl-auto 는 테이블이 한 번 생기면 제약을 바꾸지 않으므로
 * 선언이 틀린 채 배포되면 수동 DDL 없이는 되돌릴 수 없다.
 */
class UserOAuthIdentityEntityConstraintTest {

    private Map<String, String[]> uniqueConstraints() {
        Table table = UserOAuthIdentityEntity.class.getAnnotation(Table.class);
        return Arrays.stream(table.uniqueConstraints())
                .collect(Collectors.toMap(UniqueConstraint::name, UniqueConstraint::columnNames));
    }

    @Test
    void 제공자_계정은_한_유저에만_붙는다() {
        assertThat(uniqueConstraints().get("uk_user_oauth_identities_provider_subject"))
                .containsExactly("provider", "provider_subject");
    }

    @Test
    void 한_유저는_같은_제공자를_두번_연동하지_못한다() {
        assertThat(uniqueConstraints().get("uk_user_oauth_identities_user_provider"))
                .containsExactly("user_id", "provider");
    }

    @Test
    void provider는_번호가_아니라_이름으로_저장된다() throws Exception {
        // ORDINAL 이면 enum 순서를 바꾸는 순간 기존 행의 의미가 바뀐다 (GOOGLE 1→0 사고와 같은 문제).
        Enumerated enumerated = UserOAuthIdentityEntity.class.getDeclaredField("provider").getAnnotation(Enumerated.class);

        assertThat(enumerated).isNotNull();
        assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
    }

    @Test
    void provider와_provider_subject는_NOT_NULL이다() throws Exception {
        assertThat(UserOAuthIdentityEntity.class.getDeclaredField("provider").getAnnotation(Column.class).nullable()).isFalse();
        assertThat(UserOAuthIdentityEntity.class.getDeclaredField("providerSubject").getAnnotation(Column.class).nullable()).isFalse();
    }
}
