package com.danmalgi.backend.user.repository.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.danmalgi.backend.device.repository.entity.DeviceEntity;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_name_tag", columnNames = {"nickname", "tag"})
        }
)
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(length = 255)
    private String email;

    @NotNull
    @Column(name = "nickname", length = 32)
    private String name;

    @NotNull
    @Column(length = 16)
    private String tag;

    @Column
    @NotNull
    private int status;

    @Column(name = "profile_image_url", length = 512)
    private String profileImageUrl;

    @Column
    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user")
    private List<DeviceEntity> devices = new ArrayList<>();

    public void updateNameAndTag(String name, String tag) {
        this.name = name;
        this.tag = tag;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public User toDomainUser() {
        User user = new User(
            id,
            email,
            name,
            tag,
            null,
            status
        );
        user.setProfileImageUrl(profileImageUrl);
        return user;
    }

    public void updateStatus(int status) {
        this.status = status;
    }

    public static UserEntity from(User user) {
        UserEntity userEntity = new UserEntity();
        userEntity.id = user.getId();
        userEntity.email = user.getEmail();
        userEntity.name = user.getName();
        userEntity.tag = user.getTag();
        userEntity.status = user.getStatus();
        userEntity.profileImageUrl = user.getProfileImageUrl();
        return userEntity;
    }

    /**
     * 회원가입 완료 시점의 최초 INSERT 용 팩토리.
     *
     * <p>{@code id} 는 INSERT 시점에 DB 가 채운다 (IDENTITY). OAuth 신원은
     * 호출부(auth)가 {@code user_oauth_identities} 에 따로 넣는다.
     *
     * @param email 대표 이메일. 가입에 쓴 제공자가 준 값을 복사한다.
     */
    public static UserEntity registerNew(
            String email,
            String name,
            String tag,
            String profileImageUrl
    ) {
        UserEntity userEntity = new UserEntity();
        userEntity.email = email;
        userEntity.name = name;
        userEntity.tag = tag;
        userEntity.status = UserStatus.ACTIVE.getNumber();
        userEntity.profileImageUrl = profileImageUrl;
        return userEntity;
    }
}
