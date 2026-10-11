package com.danmalgi.backend.auth.repository.entity;

import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserOAuthIdentityEntityLinkTest {

    @Test
    void link_인자를_그대로_채운다() {
        UserEntity user = UserEntity.registerNew(100L, "abc@privaterelay.appleid.com", "홍길동", "00001", null);
        Instant authenticatedAt = Instant.parse("2026-10-07T00:00:00Z");

        UserOAuthIdentityEntity identity = UserOAuthIdentityEntity.link(
                user, OauthType.APPLE, "apple-sub-001", "abc@privaterelay.appleid.com", authenticatedAt);

        assertThat(identity.getUser()).isSameAs(user);
        assertThat(identity.getProvider()).isEqualTo(OauthType.APPLE);
        assertThat(identity.getProviderSubject()).isEqualTo("apple-sub-001");
        assertThat(identity.getEmail()).isEqualTo("abc@privaterelay.appleid.com");
        assertThat(identity.getLastAuthenticatedAt()).isEqualTo(authenticatedAt);
    }

    @Test
    void link_id는_DB가_채우도록_비워둔다() {
        UserEntity user = UserEntity.registerNew(100L, "test@gmail.com", "홍길동", "00001", null);

        UserOAuthIdentityEntity identity = UserOAuthIdentityEntity.link(
                user, OauthType.GOOGLE, "google-sub-123", "test@gmail.com", Instant.now());

        assertThat(identity.getId()).isNull();
    }
}
