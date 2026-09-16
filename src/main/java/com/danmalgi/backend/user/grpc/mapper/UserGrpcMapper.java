package com.danmalgi.backend.user.grpc.mapper;

import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.external.user.v1.UserProto;

public class UserGrpcMapper {
    private UserGrpcMapper() {}

    public static UserProto.User toProtoUser(User user) {
        UserProto.OauthType oauthType = UserProto.OauthType.forNumber(user.getOauthType());
        if (oauthType == null) {
            throw new IllegalStateException("Invalid oauthType value: " + user.getOauthType());
        }

        UserProto.UserStatus userStatus = UserProto.UserStatus.forNumber(user.getStatus());
        if (userStatus == null) {
            throw new IllegalStateException("Invalid userStatus value: " + user.getStatus());
        }

        UserProto.User.Builder userBuilder = UserProto.User.newBuilder()
                .setOauthType(oauthType)
                .setStatus(userStatus);

        if (user.getId() != null) {
            userBuilder.setId(user.getId());
        }

        if (user.getEmail() != null) {
            userBuilder.setEmail(user.getEmail());
        }

        if (user.getName() != null) {
            userBuilder.setName(user.getName());
        }

        if (user.getTag() != null) {
            userBuilder.setTag(user.getTag());
        }

        if (user.getProfileImageUrl() != null) {
            userBuilder.setProfileImageUrl(user.getProfileImageUrl());
        }

        return userBuilder.build();
    }

    public static UserProto.UploadProfileResponse toUploadProfileResponse(User user) {
        return UserProto.UploadProfileResponse.newBuilder()
                .setUser(toProtoUser(user))
                .build();
    }
}
