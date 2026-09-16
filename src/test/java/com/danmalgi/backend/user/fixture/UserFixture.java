package com.danmalgi.backend.user.fixture;

import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import com.danmalgi.backend.user.repository.entity.UserEntity;

public class UserFixture {

    public static User createUser(Long id, String name, String tag) {
        return new User(id, "test@test.com", name, tag, null,
                "oauth-id-" + id, OauthType.KAKAO.getNumber(), UserStatus.ACTIVE.getNumber());
    }

    public static UserEntity createUserEntity(Long id, String name, String tag) {
        return UserEntity.from(createUser(id, name, tag));
    }
}
