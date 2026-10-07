package com.danmalgi.backend.auth.repository.entity;

import com.danmalgi.backend.user.domain.model.OauthType;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 유저가 로그인에 쓰는 OAuth 신원 하나 (제공자 + 제공자 측 계정 ID).
 *
 * <p>{@code users} 에서 분리한 이유: 나중에 한 유저가 Google 과 Apple 을 함께 연동할 수 있게
 * 하려면 신원이 유저와 1:N 이어야 한다. 로그인 조회 키는 {@code (provider, provider_subject)} 다.
 *
 * <ul>
 *   <li>{@code uk_..._provider_subject}: 한 제공자 계정이 두 유저에 붙지 못하게 한다.
 *   <li>{@code uk_..._user_provider}: 한 유저가 같은 제공자를 두 번 연동하지 못하게 한다.
 *       선두 컬럼이 user_id 라 FK 조회 인덱스도 겸한다.
 * </ul>
 *
 * <p>미완성: 연동/해제 RPC 는 아직 없다. 지금은 가입 시 하나만 생긴다.
 * "마지막 신원은 해제 금지" 는 제약으로 표현할 수 없어 해제 기능을 만들 때 service 가 지켜야 한다.
 */
@Entity
@Table(
        name = "user_oauth_identities",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_oauth_identities_provider_subject",
                        columnNames = {"provider", "provider_subject"}),
                @UniqueConstraint(name = "uk_user_oauth_identities_user_provider",
                        columnNames = {"user_id", "provider"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOAuthIdentityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // 번호가 아니라 이름으로 저장한다. proto enum 재번호가 데이터 마이그레이션이 되지 않게 하려는 것이다.
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 16, nullable = false)
    private OauthType provider;

    // Google/Apple 의 sub. 이메일은 바뀌거나 릴레이 주소일 수 있어 식별에 쓰지 않는다.
    @Column(name = "provider_subject", length = 255, nullable = false)
    private String providerSubject;

    // 제공자가 마지막 로그인에서 준 이메일. users.email(대표 이메일)과 별개이고 갱신된다.
    @Column(name = "email", length = 255)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_authenticated_at", nullable = false)
    private Instant lastAuthenticatedAt;

    /** 가입(또는 이후 연동) 시점에 신원을 유저에 붙인다. */
    public static UserOAuthIdentityEntity link(
            UserEntity user,
            OauthType provider,
            String providerSubject,
            String email,
            Instant authenticatedAt
    ) {
        UserOAuthIdentityEntity identity = new UserOAuthIdentityEntity();
        identity.user = user;
        identity.provider = provider;
        identity.providerSubject = providerSubject;
        identity.email = email;
        identity.lastAuthenticatedAt = authenticatedAt;
        return identity;
    }
}
