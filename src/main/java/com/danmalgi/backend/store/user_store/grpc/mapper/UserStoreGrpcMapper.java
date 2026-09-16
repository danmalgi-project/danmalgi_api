package com.danmalgi.backend.store.user_store.grpc.mapper;

import com.danmalgi.backend.internal.user_store.v1.UserStoreProto;
import com.danmalgi.backend.user.domain.model.User;

public class UserStoreGrpcMapper {
    private UserStoreGrpcMapper() {}

    public static UserStoreProto.User toProtoUser(User user) {
        UserStoreProto.User.Builder userBuilder = UserStoreProto.User.newBuilder()
                .setId(user.getId())
                .setEmail(user.getEmail())
                .setName(user.getName())
                .setTag(user.getTag())
                .setOauthType(user.getOauthType())
                .setStatus(user.getStatus());

        if (user.getProfileImageUrl() != null) {
            userBuilder.setProfileImageUrl(user.getProfileImageUrl());
        }

        return userBuilder.build();
    }
}
