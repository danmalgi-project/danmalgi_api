package com.danmalgi.backend.auth.repository.entity;

import com.danmalgi.backend.user.repository.entity.UserEntity;
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
 * Apple 유저의 refresh token. 계정 삭제 시 Apple revoke 에 쓴다 (이슈 #3 의 3-4).
 *
 * <p>{@code users} 와 분리한 이유: Apple 유저만 갖는 값이고, 평문 시크릿에 준하는 값이라
 * {@code users} 를 읽는 모든 경로(캐시 포함)에 실려 다니지 않게 한다.
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
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

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
