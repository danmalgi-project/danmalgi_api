package com.danmalgi.backend.auth.infrastructure.pending;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;

import lombok.RequiredArgsConstructor;

/**
 * 닉네임/태그 입력 전 OAuth 프로필의 Redis 임시 보관소.
 *
 * <p>키 두 개로 인덱싱한다.
 * <ul>
 *   <li>{@code auth:pending:user:{userId}} — Register 가 읽는 본체
 *   <li>{@code auth:pending:oauth:{oauthType}:{identifyId}} — 계정 → userId 인덱스.
 *       TTL 내 재로그인 시 <b>같은 userId 를 재사용</b>해 선행 발급 토큰을 살려둔다
 * </ul>
 *
 * <p>prefix 가 {@code auth:} 라 chat/webrtc 와 공유하는 {@code user:{id}} /
 * {@code device:{deviceId}} 계약과 겹치지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PendingAuthStore {

    /**
     * 닉네임 입력 화면 체류 시간 기준.
     *
     * <p>Redis 기본 10분은 중복 태그 거부 후 재입력이나 앱 백그라운드 전환을 견디기에 짧고,
     * 시간 단위는 토큰 유출 시 회원가입을 완료할 수 있는 창을 불필요하게 넓힌다.
     */
    private static final Duration PENDING_TTL = Duration.ofMinutes(30);

    private static final String USER_KEY_PREFIX = "auth:pending:user:";
    private static final String OAUTH_KEY_PREFIX = "auth:pending:oauth:";

    private final RedisTemplate<String, Object> redisTemplate;

    public Optional<Long> findUserId(int oauthType, String identifyId) {
        Object value = redisTemplate.opsForValue().get(oauthKey(oauthType, identifyId));
        // JSON 역직렬화가 작은 수를 Integer 로 돌려줄 수 있어 Number 로 받는다.
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        return Optional.empty();
    }

    /**
     * OAuth 계정에 userId 를 원자적으로 선점한다. 이미 선점되어 있으면 {@code false}.
     *
     * <p>동시 Authorization 두 건이 각자 nextval 한 id 로 세션을 만들면 패배한 쪽의
     * 토큰이 무효해진다. SETNX 로 승자를 하나만 남긴다.
     */
    public boolean claimUserId(int oauthType, String identifyId, Long userId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue()
                        .setIfAbsent(oauthKey(oauthType, identifyId), userId, PENDING_TTL)
        );
    }

    /**
     * 프로필 본체 키와 oauth 인덱스 키를 모두 (재)기록한다.
     *
     * <p>oauth 키를 {@code expire} 가 아니라 {@code set} 으로 다시 쓰는 이유는,
     * oauth 키만 먼저 만료된 어긋난 상태를 self-heal 하기 위해서다. 같은 계정이면
     * 값이 동일하므로 멱등하다.
     */
    public void save(PendingOAuthProfile profile) {
        redisTemplate.opsForValue().set(userKey(profile.getUserId()), profile, PENDING_TTL);
        redisTemplate.opsForValue().set(
                oauthKey(profile.getOauthType(), profile.getIdentifyId()),
                profile.getUserId(),
                PENDING_TTL
        );
    }

    public Optional<PendingOAuthProfile> find(Long userId) {
        Object value = redisTemplate.opsForValue().get(userKey(userId));
        if (value instanceof PendingOAuthProfile profile) {
            return Optional.of(profile);
        }
        return Optional.empty();
    }

    public void delete(PendingOAuthProfile profile) {
        redisTemplate.delete(userKey(profile.getUserId()));
        redisTemplate.delete(oauthKey(profile.getOauthType(), profile.getIdentifyId()));
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String oauthKey(int oauthType, String identifyId) {
        return OAUTH_KEY_PREFIX + oauthType + ":" + identifyId;
    }
}
