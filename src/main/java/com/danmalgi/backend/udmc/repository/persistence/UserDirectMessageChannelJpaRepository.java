package com.danmalgi.backend.udmc.repository.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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
     * 채널의 참여 행을 <b>행 락을 잡으면서</b> 읽는다 (나가기 전용).
     *
     * <p>나가기의 자연 키는 {@code channelId} 다. 참여자 집합 해시({@code ParticipantsLockKey})를
     * 쓰지 않는 이유는 <b>그룹이 같은 참여자 집합을 가진 방을 여러 개 가질 수 있어</b>
     * 집합이 채널을 식별하지 못하기 때문이다. 집합 키로 잠그면 무관한 방끼리 직렬화된다.
     *
     * <p>{@code findAllByDirectMessageChannelId} 에 락을 붙이지 않고 메서드를 따로 둔 이유는
     * 그쪽 호출부 중 둘이 읽기 경로이기 때문이다 — {@code UserDirectMessageChannelService.toDirectMessage}
     * 는 채널 목록 조회에서 방마다 호출되고, {@code DeviceService.getDevicesByChannelId} 는 푸시 대상을 읽는다.
     * 거기에 락이 번지면 목록 한 번 볼 때마다 전 채널의 참여 행을 잠근다.
     *
     * <p><b>EAGER 연관이 INNER join 으로 나가는 데 의존한다.</b> {@code user} 와
     * {@code directMessageChannel} 의 {@code @JoinColumn} 이 둘 다 {@code nullable = false} 라
     * Hibernate 가 INNER join 을 쓰고, 그래야 PostgreSQL 에서 {@code for update of <루트별칭>} 이 붙는다.
     * FK 를 nullable 로 바꾸거나 left join 이 섞이면 Hibernate 가 follow-on locking
     * (락 없이 조회한 뒤 행마다 따로 잠그기)으로 조용히 떨어져 <b>경쟁 창이 다시 열린다.</b>
     * 잠금 대상은 루트(UDMC)뿐이고 join 된 {@code users}/{@code direct_message_channels} 행은 잠기지 않는다.
     *
     * <p>반드시 나가기 트랜잭션 안에서 호출해야 한다. 단독 호출되면 트랜잭션이 메서드 끝에서
     * 커밋되며 락이 즉시 풀린다. 리포에 lock timeout 설정이 없어 무한 대기를 막으려고 3초를 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            SELECT u
            FROM UserDirectMessageChannelEntity u
            WHERE u.directMessageChannel.id = :channelId
            ORDER BY u.id
            """)
    List<UserDirectMessageChannelEntity> lockAllByDirectMessageChannelId(@Param("channelId") Long channelId);

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
