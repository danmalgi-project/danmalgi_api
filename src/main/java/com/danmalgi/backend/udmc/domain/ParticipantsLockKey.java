package com.danmalgi.backend.udmc.domain;

import java.util.List;

/**
 * 참여자 집합을 PostgreSQL advisory lock 키(64bit)로 변환한다.
 *
 * <p>참여자 id 를 오름차순 정렬한 뒤 FNV-1a 64bit 해시를 적용하므로 요청 순서와 무관하게 항상 같은 키가 나온다.
 * {@code String.hashCode()} 는 32bit 라 bigint 키 공간을 절반 이하로 낭비하므로 쓰지 않는다.
 * 해시 충돌은 무해하다 — 무관한 참여자 조합의 채널 생성이 불필요하게 직렬화될 뿐이다.
 */
public final class ParticipantsLockKey {

    private static final long FNV_64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_64_PRIME = 0x100000001b3L;

    private ParticipantsLockKey() {
    }

    public static long of(List<Long> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            throw new IllegalArgumentException("participantIds must not be empty");
        }

        long hash = FNV_64_OFFSET_BASIS;
        for (Long participantId : participantIds.stream().sorted().toList()) {
            long value = participantId;
            for (int shift = 56; shift >= 0; shift -= 8) {
                hash ^= (value >>> shift) & 0xFFL;
                hash *= FNV_64_PRIME;
            }
        }
        return hash;
    }
}
