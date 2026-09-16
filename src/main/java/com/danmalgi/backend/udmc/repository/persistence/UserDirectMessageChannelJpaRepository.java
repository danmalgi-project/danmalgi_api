package com.danmalgi.backend.udmc.repository.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;

@Repository
public interface UserDirectMessageChannelJpaRepository extends JpaRepository<UserDirectMessageChannelEntity, Long> {
    List<UserDirectMessageChannelEntity> findAllByUserId(Long userId);
    List<UserDirectMessageChannelEntity> findAllByDirectMessageChannelId(Long channelId);
    List<UserDirectMessageChannelEntity> findAllByDirectMessageChannel_IdAndUser_IdIn(Long channelId, List<Long> userIds);
    Optional<UserDirectMessageChannelEntity> findByUser_IdAndDirectMessageChannel_Id(Long userId, Long channelId);

    /**
     * 참여자 집합이 정확히 일치하는 1:1 채널 id 를 오래된 순으로 반환한다.
     *
     * <p>{@code HAVING COUNT} 는 요청 참여자가 전원 있음을, {@code NOT EXISTS} 는 그 외 참여자가 없음을 보장한다.
     * 둘 중 하나만으로는 3인 채널이 2인 조건에 오매칭된다.
     * {@code isGroup = false} 는 성능 조건이 아니라 정확성 요건이다 — 그룹에서 한 명이 나가면
     * 남은 멤버십이 2행이 되어 1:1 조건에 걸리기 때문이다.
     * 기존 중복 데이터가 있을 수 있어 결과가 여러 건일 수 있으므로 {@code ORDER BY} 로 항상 같은 채널을 고른다.
     */
    @Query("""
            SELECT c.id
            FROM UserDirectMessageChannelEntity u
            JOIN u.directMessageChannel c
            WHERE u.user.id IN :participantIds
              AND c.isGroup = false
              AND NOT EXISTS (
                  SELECT x.id
                  FROM UserDirectMessageChannelEntity x
                  WHERE x.directMessageChannel.id = c.id
                    AND x.user.id NOT IN :participantIds
              )
            GROUP BY c.id
            HAVING COUNT(u.id) = :participantCount
            ORDER BY c.id ASC
            """)
    List<Long> findChannelIdsByExactParticipants(
            @Param("participantIds") List<Long> participantIds,
            @Param("participantCount") long participantCount
    );

    /**
     * 참여자 조합 단위 트랜잭션 advisory lock 을 획득한다 (경쟁 조건 방어).
     *
     * <p>반드시 채널 생성 트랜잭션 안에서, 그리고 조회보다 <b>먼저</b> 호출해야 한다.
     * {@code pg_advisory_xact_lock} 은 트랜잭션 종료 시 자동 해제되므로 별도 트랜잭션에서 잡으면 효과가 없다.
     * 함수를 FROM 절에 두는 이유는 반환 타입이 {@code void} 라 select 목록에 두면 매핑이 드라이버 구현에 의존하기 때문이다.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
    Integer acquireChannelParticipantsLock(@Param("lockKey") long lockKey);
}
