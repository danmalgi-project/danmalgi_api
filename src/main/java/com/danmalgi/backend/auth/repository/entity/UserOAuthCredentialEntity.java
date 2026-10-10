package com.danmalgi.backend.auth.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * OAuth 신원의 refresh token. 계정 삭제·연동 해제 시 제공자 revoke 에 쓴다 (이슈 #3 의 3-4).
 *
 * <p>키가 user 가 아니라 신원({@code user_oauth_identities})인 이유: 토큰은 제공자 계정에
 * 속한 값이라, 연동 해제 때 "어느 신원의 토큰을 revoke 할지" 가 스키마에 드러나야 한다.
 * 신원 테이블과도 분리한 이유는 평문 시크릿에 준하는 값이 로그인 조회마다 실려 다니지 않게 하려는 것이다.
 *
 * <p>쓰기는 {@code UserOAuthCredentialJpaRepository#upsert} 네이티브 쿼리로만 한다.
 * 이 엔티티는 {@code ddl-auto} 가 테이블과 FK 를 만들게 하는 스키마 선언이다.
 * 테이블이 한 번 생긴 뒤의 제약 변경은 자동 반영되지 않는다.
 *
 * <p>테이블명이 {@code user_apple_credentials} 에서 바뀌었다. ddl-auto 는 리네임을 못 하므로
 * 배포 전 {@code ALTER TABLE user_apple_credentials RENAME TO user_oauth_credentials;} 를
 * 수동 실행해야 한다. 빠뜨리면 빈 테이블이 새로 생기고 기존 Apple 토큰이 조용히 고아가 된다.
 */
@Entity
@Table(name = "user_oauth_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOAuthCredentialEntity {
    @Id
    @Column(name = "identity_id")
    private Long identityId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_id")
    private UserOAuthIdentityEntity identity;

    // Apple revoke 는 code 를 발급받은 클라이언트(iOS Bundle ID / Android Services ID)로 해야 한다.
    // Google 은 revoke 에 client_id 를 요구하지 않지만, 교환에 쓴 값을 그대로 남겨 둔다.
    @NotNull
    @Column(name = "client_id", nullable = false)
    private String clientId;

    // CredentialCipher 암호문. 제공자들이 refresh token 길이를 문서화하지 않아 TEXT 로 둔다.
    @NotNull
    @Column(name = "refresh_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
