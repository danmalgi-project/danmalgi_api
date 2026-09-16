package com.danmalgi.backend.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * OAuth 인증은 끝났지만 닉네임/태그가 확정되지 않은 프로필.
 *
 * <p>{@link com.danmalgi.backend.user.domain.model.User} 는 {@code validate()} 가
 * name/tag 를 필수로 강제하므로 이 상태를 표현할 수 없다.
 *
 * <p>{@code userId} 는 {@code users} 시퀀스에서 미리 뽑은 값이고 해당 행은 <b>아직 없다</b>.
 * Register 시점에 이 id 로 최초 INSERT 된다.
 *
 * <p>Redis 직렬화 대상이므로 public {@code @NoArgsConstructor} 와 setter 가 필요하다
 * ({@code GenericJacksonJsonRedisSerializer} + default typing).
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PendingOAuthProfile {
    private Long userId;
    private String email;
    private String identifyId;
    private int oauthType;
    // 현재 어댑터는 이 값을 세팅하지 않는다 (Google picture 클레임 미사용) — 항상 null 이다.
    // R2 key 가 아니라 외부 URL 이 될 값이므로 applyPublicProfileImageUrl 을 적용하지 않는다.
    private String profileImageUrl;
}
