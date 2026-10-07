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
 * Apple 신원의 refresh token. 계정 삭제·연동 해제 시 Apple revoke 에 쓴다 (이슈 #3 의 3-4).
 *
 * <p>키가 user 가 아니라 신원({@code user_oauth_identities})인 이유: 토큰은 Apple 계정에
 * 속한 값이라, 연동 해제 때 "어느 신원의 토큰을 revoke 할지" 가 스키마에 드러나야 한다.
 * 신원 테이블과도 분리한 이유는 평문 시크릿에 준하는 값이 로그인 조회마다 실려 다니지 않게 하려는 것이다.
 *
 * <p>쓰기는 {@code UserAppleCredentialJpaRepository#upsert} 네이티브 쿼리로만 한다.
 * 이 엔티티는 {@code ddl-auto} 가 테이블과 FK 를 만들게 하는 스키마 선언이다.
 * 테이블이 한 번 생긴 뒤의 제약 변경은 자동 반영되지 않는다.
 */
@Entity
@Table(name = "user_apple_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAppleCredentialEntity {
    @Id
    @Column(name = "identity_id")
    private Long identityId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_id")
    private UserOAuthIdentityEntity identity;

    // revoke 도 code 를 발급받은 클라이언트(iOS Bundle ID / Android Services ID)로 해야 한다.
    @NotNull
    @Column(name = "client_id", nullable = false)
    private String clientId;

    // CredentialCipher 암호문. Apple 이 refresh token 길이를 문서화하지 않아 TEXT 로 둔다.
    @NotNull
    @Column(name = "refresh_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
