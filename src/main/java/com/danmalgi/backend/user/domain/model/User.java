package com.danmalgi.backend.user.domain.model;

import java.io.Serial;
import java.io.Serializable;

import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.user.domain.exception.UserValidateException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class User {
    private Long id;
    private String email;
    private String name;
    private String tag;
    private String deviceId;
    private String identifyId;
    private int oauthType;
    private int status;
    private String profileImageUrl;

    private static final int MAX_NAME_LENGTH = 16;
    private static final int MAX_TAG_LENGTH = 5;

    public User(Long id, String email, String name, String tag, String deviceId) {
        this(id, email, name, tag, deviceId, null, 0, 0);
        validate();
    }

    public User(Long id, String email, String name, String tag, String deviceId, String identifyId, int oauthType, int status) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.tag = tag;
        this.deviceId = deviceId;
        this.identifyId = identifyId;
        this.oauthType = oauthType;
        this.status = status;
        validate();
    }

    public void applyPublicProfileImageUrl(R2Uploader r2Uploader) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return;
        }
        this.profileImageUrl = r2Uploader.toPublicUrl(profileImageUrl);
    }

    private void validate() {
        if (name == null || name.isBlank()) {
            throw new UserValidateException("name is null or blank");
        }

        if (tag == null || tag.isBlank()) {
            throw new UserValidateException("tag is null or blank");
        }

        if (name.length() > MAX_NAME_LENGTH) {
            throw new UserValidateException("name must be at most 16 characters");
        }

        if (tag.length() > MAX_TAG_LENGTH) {
            throw new UserValidateException("tag must be at most 5 characters");
        }
    }
}
