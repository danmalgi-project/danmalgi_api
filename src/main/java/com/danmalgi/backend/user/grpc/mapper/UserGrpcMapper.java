package com.danmalgi.backend.user.grpc.mapper;

import java.util.List;

import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.external.user.v1.UserProto;

public class UserGrpcMapper {
    private UserGrpcMapper() {}

    public static UserProto.User toProtoUser(User user) {
        UserProto.UserStatus userStatus = UserProto.UserStatus.forNumber(user.getStatus());
        if (userStatus == null) {
            throw new IllegalStateException("Invalid userStatus value: " + user.getStatus());
        }

        UserProto.User.Builder userBuilder = UserProto.User.newBuilder()
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

    public static UserProto.SearchUserResponse toSearchUserResponse(List<User> users) {
        UserProto.SearchUserResponse.Builder builder = UserProto.SearchUserResponse.newBuilder();
        users.forEach(user -> builder.addUsers(toProtoSearchedUser(user)));
        return builder.build();
    }

    // 타인에게 보여주는 공개 프로필이라 email·status 는 담지 않는다.
    private static UserProto.SearchedUser toProtoSearchedUser(User user) {
        UserProto.SearchedUser.Builder builder = UserProto.SearchedUser.newBuilder();

        if (user.getId() != null) {
            builder.setId(user.getId());
        }

        if (user.getName() != null) {
            builder.setName(user.getName());
        }

        if (user.getTag() != null) {
            builder.setTag(user.getTag());
        }

        if (user.getProfileImageUrl() != null) {
            builder.setProfileImageUrl(user.getProfileImageUrl());
        }

        return builder.build();
    }
}
