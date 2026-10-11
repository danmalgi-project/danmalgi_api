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
 * <p>세션은 {@code users.id} 가 아니라 호출부(AuthService)가 발급하는 UUID 로 식별한다.
 * 키 두 종류를 쓴다.
 * <ul>
 *   <li>{@code auth:pending:{sessionId}} — 프로필 본체. Register 가 읽는다
 *   <li>{@code auth:registered:{sessionId}} — Register 완료 마커(값은 생성된 userId).
 *       응답이 유실돼 같은 pending 토큰으로 재시도해도 멱등 성공시키기 위함이다
 * </ul>
 *
 * <p>재로그인마다 새 UUID + 새 토큰이 나가므로, 선행 발급 토큰을 살리려고 같은 식별자를
 * 재사용할 필요가 없다. 버려진 세션은 TTL 로 자연 소멸한다.
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
     *
     * <p>완료 마커에도 같은 값을 쓴다 — pending 세션의 유효기간과 재시도 창을 분리할
     * 근거가 아직 없다.
     */
    private static final Duration PENDING_TTL = Duration.ofMinutes(30);

    private static final String PENDING_KEY_PREFIX = "auth:pending:";
    private static final String REGISTERED_KEY_PREFIX = "auth:registered:";

    private final RedisTemplate<String, Object> redisTemplate;

    public void save(String sessionId, PendingOAuthProfile profile) {
        redisTemplate.opsForValue().set(pendingKey(sessionId), profile, PENDING_TTL);
    }

    public Optional<PendingOAuthProfile> find(String sessionId) {
        Object value = redisTemplate.opsForValue().get(pendingKey(sessionId));
        if (value instanceof PendingOAuthProfile profile) {
            return Optional.of(profile);
        }
        return Optional.empty();
    }

    public void delete(String sessionId) {
        redisTemplate.delete(pendingKey(sessionId));
    }

    /** Register 가 INSERT 에 성공한 직후, 응답 유실에 대비해 완료 마커를 남긴다. */
    public void saveCompleted(String sessionId, Long userId) {
        redisTemplate.opsForValue().set(registeredKey(sessionId), userId, PENDING_TTL);
    }

    public Optional<Long> findCompletedUserId(String sessionId) {
        Object value = redisTemplate.opsForValue().get(registeredKey(sessionId));
        // JSON 역직렬화가 작은 수를 Integer 로 돌려줄 수 있어 Number 로 받는다.
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        return Optional.empty();
    }

    private String pendingKey(String sessionId) {
        return PENDING_KEY_PREFIX + sessionId;
    }

    private String registeredKey(String sessionId) {
        return REGISTERED_KEY_PREFIX + sessionId;
    }
}
