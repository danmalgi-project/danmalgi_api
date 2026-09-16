package com.danmalgi.backend.user.repository.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import com.danmalgi.backend.device.repository.entity.DeviceEntity;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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
                @UniqueConstraint(name = "uk_users_name_tag", columnNames = {"nickname", "tag"}),
                @UniqueConstraint(name = "uk_users_oauth_type_identify_id", columnNames = {"oauth_type", "identify_id"})
        }
)
public class UserEntity implements Persistable<Long> {
    @Id
    // @GeneratedValue 없음 (assigned id). Authorization 단계에서 nextUserId() 로 확보한
    // 값을 그대로 PK 로 쓴다. ddl-auto: update 는 컬럼 default/identity 를 바꾸지 않으므로
    // 실 스키마는 그대로다.
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

    @NotNull
    @Column(name = "identify_id", length = 128)
    private String identifyId;

    @NotNull
    @Column(name = "oauth_type")
    private int oauthType;

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

    /**
     * assigned id 전략이라 Spring Data 가 id 만으로는 신규 여부를 판단할 수 없다.
     *
     * <p>이 플래그가 없으면 {@code save()} 가 항상 {@code merge} 를 타고, merge 는
     * 해당 id 의 행이 이미 있을 때 INSERT 대신 <b>조용히 UPDATE</b> 한다 (다른 유저 행
     * 덮어쓰기). {@code persist} 로 보내야 PK 충돌이 제약 위반으로 드러난다.
     *
     * <p>{@code @Getter(AccessLevel.NONE)} 은 Lombok 이 이 필드용 {@code isNew()} 를
     * 생성해 아래 {@code Persistable} 구현과 겹치는 것을 막는다 — 접근자는 하나뿐이다.
     */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = false;

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
            identifyId,
            oauthType,
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
        userEntity.identifyId = user.getIdentifyId();
        userEntity.oauthType = user.getOauthType();
        userEntity.status = user.getStatus();
        userEntity.profileImageUrl = user.getProfileImageUrl();
        userEntity.isNew = user.getId() == null;
        return userEntity;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /** 같은 인스턴스를 두 번 save 할 때 중복 persist 를 막는다. */
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    /**
     * 회원가입 완료 시점의 최초 INSERT 용 팩토리.
     *
     * <p>{@code id} 는 Authorization 단계에서 {@code nextUserId()} 로 확보한 값이다.
     * 인자를 원시 타입으로 받는 이유는 {@code auth/domain/model/PendingOAuthProfile} 을
     * 참조하면 {@code user/repository} → {@code auth/domain} 역방향 의존으로 패키지
     * 순환이 생기기 때문이다.
     */
    public static UserEntity registerNew(
            Long id,
            String email,
            String name,
            String tag,
            String identifyId,
            int oauthType,
            String profileImageUrl
    ) {
        UserEntity userEntity = new UserEntity();
        userEntity.id = id;
        userEntity.email = email;
        userEntity.name = name;
        userEntity.tag = tag;
        userEntity.identifyId = identifyId;
        userEntity.oauthType = oauthType;
        userEntity.status = UserStatus.ACTIVE.getNumber();
        userEntity.profileImageUrl = profileImageUrl;
        userEntity.isNew = true;
        return userEntity;
    }
}
