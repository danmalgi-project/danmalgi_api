package com.danmalgi.backend.auth.infrastructure.replay;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.danmalgi.backend.user.domain.model.OauthType;

import lombok.RequiredArgsConstructor;

/**
 * 검증을 통과한 idToken 의 nonce 를 1회용으로 소비한다.
 *
 * <p>nonce 검증만으로는 같은 (idToken, raw_nonce) 쌍을 exp 안에서 몇 번이든 다시 보낼 수 있다.
 * 기존 유저는 code 교환에 실패해도 로그인되고 우리 JWT 는 만료가 없으므로, 한 번 새어 나간
 * 쌍이 영구적인 세션이 된다. 그래서 nonce 를 키로 SETNX 해 두 번째 사용을 거절한다.
 *
 * <p>키는 {@code auth:oauth:used-nonce:{oauthType 이름}:{nonce 클레임}} 이다. Apple 의 nonce
 * 클레임은 이미 sha256 hex 라 원문이 Redis 에 남지 않는다. prefix 가 {@code auth:} 라
 * chat/webrtc 와 공유하는 키 계약과 겹치지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OAuthNonceReplayStore {
    private static final String KEY_PREFIX = "auth:oauth:used-nonce:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 처음 쓰는 nonce 면 {@code true}, 이미 쓴 nonce 면 {@code false}.
     *
     * @param ttl 토큰이 만료될 때까지. 만료된 토큰은 어차피 서명 검증에서 걸리므로 그 뒤로는 기억할 필요가 없다.
     */
    public boolean markUsed(OauthType oauthType, String nonce, Duration ttl) {
        // 파이프라인/트랜잭션 모드에서 null 이 올 수 있다. 판단할 수 없으면 거절 쪽으로 둔다.
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key(oauthType, nonce), Boolean.TRUE, ttl));
    }

    private String key(OauthType oauthType, String nonce) {
        return KEY_PREFIX + oauthType + ":" + nonce;
    }
}
