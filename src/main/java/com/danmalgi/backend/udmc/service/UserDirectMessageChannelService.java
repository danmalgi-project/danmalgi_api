package com.danmalgi.backend.udmc.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.domain.ParticipantsLockKey;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDirectMessageChannelService {
    private final UserDirectMessageChannelJpaRepository udmcJpaRepository;
    private final DirectMessageChannelJpaRepository dmcJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public Map<Long, String> getChannelNames(Long channelId, List<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        udmcJpaRepository.findAllByDirectMessageChannel_IdAndUser_IdIn(channelId, userIds)
                .forEach(e -> result.put(e.getUser().getId(), e.getChannelName()));
        return result;
    }

    public List<DirectMessage> getDirectMessagesByUserId(Long userId) {
        List<UserDirectMessageChannelEntity> myChannels = udmcJpaRepository.findAllByUserId(userId);

        return myChannels.stream()
                .map(this::toDirectMessage)
                .toList();
    }

    public UserDirectMessageChannelEntity getUserChannel(Long userId, Long channelId) {
        return udmcJpaRepository.findByUser_IdAndDirectMessageChannel_Id(userId, channelId)
                .orElseThrow(() -> new NoSuchElementException(
                        "user direct message channel not found: userId=" + userId + ", channelId=" + channelId
                ));
    }

    public DirectMessage getDirectMessage(Long userId, Long channelId) {
        return toDirectMessage(getUserChannel(userId, channelId));
    }

    /**
     * 참여 중이면 채널을 돌려주고, 아니면 빈 {@code Optional} 을 준다.
     *
     * <p>{@link #getDirectMessage(Long, Long)} 과 달리 <b>없는 것을 예외로 보지 않는다.</b>
     * 1:1 dedupe 로 찾은 채널을 다시 로드할 때 쓴다 — 그 사이 상대가 나가 방이 사라졌을 수 있고,
     * 생성 요청에 {@code NOT_FOUND} 를 돌려주는 것은 틀린 답이라 호출자가 새로 만들어야 한다.
     */
    public Optional<DirectMessage> findDirectMessage(Long userId, Long channelId) {
        return udmcJpaRepository.findByUser_IdAndDirectMessageChannel_Id(userId, channelId)
                .map(this::toDirectMessage);
    }

    /**
     * 참여자 집합이 정확히 일치하는 기존 1:1 채널을 찾는다.
     *
     * <p><b>반드시 채널 생성 트랜잭션 안에서 호출해야 한다.</b> advisory lock 을 먼저 잡고 조회하는 순서가
     * 경쟁 조건 방어의 전부이며, {@code pg_advisory_xact_lock} 은 트랜잭션 종료 시 해제된다.
     * 별도 트랜잭션(REQUIRES_NEW)에서 호출하면 락이 즉시 풀려 아무것도 막지 못한다.
     *
     * <p>{@code ParticipantsLockKey} 는 <b>생성 dedupe 전용</b>이다. 나가기는 같은 키를 잡지 않는다 —
     * 삭제의 자연 키는 채널이고, 그룹은 같은 참여자 집합을 가진 방을 여러 개 가질 수 있어
     * 집합 키가 채널을 식별하지 못하기 때문이다. 나가기 쪽은
     * {@link #lockParticipants(Long)} 의 행 락으로 직렬화한다.
     *
     * <p>여기서 찾은 채널이 <b>호출자가 다시 로드할 때까지 살아 있다는 보장은 없다.</b>
     * 나가기가 이 락을 기다리지 않으므로, READ COMMITTED 아래서 다음 문장이 이미 지워진 채널을 볼 수 있다.
     * 재로드가 비었을 때의 처리는 호출자({@code DirectMessageService.createChannel}) 책임이다.
     */
    @Transactional
    public Optional<Long> lockAndFindOneToOneChannelId(List<Long> participantIds) {
        udmcJpaRepository.acquireChannelParticipantsLock(ParticipantsLockKey.of(participantIds));

        return udmcJpaRepository
                .findChannelIdsByExactParticipants(participantIds, participantIds.size())
                .stream()
                .findFirst();
    }

    @Transactional
    public void updateChannelName(Long channelId, String channelName) {
        List<UserDirectMessageChannelEntity> memberships = udmcJpaRepository.findAllByDirectMessageChannelId(channelId);
        memberships.forEach(membership -> membership.updateChannelName(channelName));
        udmcJpaRepository.saveAll(memberships);
    }

    /**
     * 채널의 참여 행을 행 락을 잡고 읽는다. <b>나가기 전용이다.</b>
     *
     * <p>반환된 목록은 이 트랜잭션 동안 <b>권위 있는 참여자 집합</b>이다. 같은 방에서 동시에
     * 나가는 요청은 여기서 줄을 서고, 뒤에 들어온 쪽은 앞사람이 지운 결과를 반영해 다시 읽는다.
     * 그래서 "내가 마지막 한 명인가" 를 이 목록의 크기로 판정할 수 있다 —
     * 지우기 전에 따로 COUNT 하면 양쪽 다 옛 값을 읽어 아무도 방을 안 지우는 창이 생긴다.
     *
     * <p>참여자 집합 해시가 아니라 채널로 잠그는 이유는
     * {@link UserDirectMessageChannelJpaRepository#lockAllByDirectMessageChannelId(Long)} 에 적었다.
     *
     * <p><b>반드시 나가기 트랜잭션 안에서 호출해야 한다.</b> 행 락은 트랜잭션 종료 시 풀리므로
     * 단독으로 부르면 결과를 받는 시점엔 이미 아무것도 잠겨 있지 않다.
     */
    @Transactional
    public List<UserDirectMessageChannelEntity> lockParticipants(Long channelId) {
        return udmcJpaRepository.lockAllByDirectMessageChannelId(channelId);
    }

    /**
     * 넘겨받은 참여 행을 지운다. DMC 행은 건드리지 않는다 — 방을 지울지는 호출자가 판단한다.
     *
     * <p><b>{@link #lockParticipants(Long)} 가 돌려준 엔티티만 넘겨야 한다.</b> 그 행들은 같은
     * 트랜잭션이 잠그고 있어 중간에 사라질 수 없고, 그래서 0행 DELETE 로 Hibernate 가
     * {@code StaleStateException} 을 던지는 일이 생기지 않는다. 락 없이 읽은 목록을 넘기면
     * 그 보장이 깨지는데 시그니처로는 막지 못한다.
     */
    @Transactional
    public void deleteMemberships(List<UserDirectMessageChannelEntity> memberships) {
        udmcJpaRepository.deleteAll(memberships);
    }

    @Transactional
    public DirectMessage createUserChannel(Long userId, List<Long> friendIds, Long channelId) {
        DirectMessageChannelEntity channelEntity = dmcJpaRepository.getReferenceById(channelId);

        List<Long> participantIds = Stream.concat(Stream.of(userId), friendIds.stream()).toList();

        List<UserEntity> participants = participantIds.stream()
                .map(userJpaRepository::getReferenceById)
                .toList();

        List<UserDirectMessageChannelEntity> entities = participants.stream()
                .map(participant -> UserDirectMessageChannelEntity.of(
                        participant,
                        channelEntity,
                        buildChannelName(participant, participants)
                ))
                .toList();

        udmcJpaRepository.saveAll(entities);

		List<User> users = entities.stream()
			.map(p -> p.getUser().toDomainUser())
			.toList();

        return entities.stream()
                .filter(e -> e.getUser().getId().equals(userId))
                .findFirst()
                .map(e -> new DirectMessage(
                        e.getDirectMessageChannel().getId(),
                        e.getChannelName(),
                        e.getDirectMessageChannel().getIsGroup(),
                        e.getDirectMessageChannel().getChannelImageUrl(),
                        users,
                        null
				))
                .orElseThrow(() -> new NoSuchElementException(
                        "user direct message channel not found: userId=" + userId + ", channelId=" + channelId
                ));
    }

    private String buildChannelName(UserEntity participant, List<UserEntity> allParticipants) {
        return allParticipants.stream()
                .filter(other -> !other.getId().equals(participant.getId()))
                .map(UserEntity::getName)
                .collect(Collectors.joining(","));
    }

    private DirectMessage toDirectMessage(UserDirectMessageChannelEntity membership) {
        Long channelId = membership.getDirectMessageChannel().getId();
        List<User> users = udmcJpaRepository.findAllByDirectMessageChannelId(channelId)
                .stream()
                .map(participant -> participant.getUser().toDomainUser())
                .toList();

        return new DirectMessage(
                channelId,
                membership.getChannelName(),
                membership.getDirectMessageChannel().getIsGroup(),
                membership.getDirectMessageChannel().getChannelImageUrl(),
                users,
                null
        );
    }
}
