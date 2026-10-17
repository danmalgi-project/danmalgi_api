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
     * 참여자 집합이 정확히 일치하는 기존 1:1 채널을 찾는다.
     *
     * <p><b>반드시 채널 생성 트랜잭션 안에서 호출해야 한다.</b> advisory lock 을 먼저 잡고 조회하는 순서가
     * 경쟁 조건 방어의 전부이며, {@code pg_advisory_xact_lock} 은 트랜잭션 종료 시 해제된다.
     * 별도 트랜잭션(REQUIRES_NEW)에서 호출하면 락이 즉시 풀려 아무것도 막지 못한다.
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
     * 호출자의 참여 행 하나만 지운다. <b>그룹 채널 전용 경로다.</b>
     *
     * <p>DMC 행은 건드리지 않는다. 그룹은 혼자 남아도 방이 유지돼야 하기 때문이다.
     * 1:1 은 두 사람 모두에게서 방이 사라져야 하므로 이 메서드가 아니라
     * {@link #lockAndDeleteOneToOneMemberships(Long)} 를 쓴다.
     * 방이 비는지(참여자 0명) 판단해 DMC 를 지우는 책임은 호출자인
     * {@code DirectMessageService.leaveChannel} 에 있다.
     */
    @Transactional
    public void leaveChannel(Long userId, Long channelId) {
        UserDirectMessageChannelEntity membership = getUserChannel(userId, channelId);

        udmcJpaRepository.delete(membership);
    }

    public long countParticipants(Long channelId) {
        return udmcJpaRepository.countByDirectMessageChannelId(channelId);
    }

    /**
     * 1:1 채널의 참여 행을 <b>전원분</b> 지운다.
     *
     * <p>1:1 은 한 명이 나가면 방 자체가 없어진다. 한쪽 행만 지우면 남은 사람의 목록에
     * 참여자 1명짜리 방이 남고, 나간 사람이 같은 상대와 다시 방을 만들 때
     * {@code HAVING COUNT = 2} 에 걸리지 않아 중복 방이 생긴다.
     *
     * <p>채널 생성의 1:1 dedupe 와 같은 {@code ParticipantsLockKey} 로 advisory lock 을 잡는다.
     * 락이 없으면 생성 측이 "지금 지워지는 중인 채널" 을 찾아 그 id 를 클라이언트에 돌려줄 수 있다.
     * 참여자 조회를 락보다 먼저 하는 것은 락 키를 만들려면 참여자 id 가 필요하기 때문이고,
     * 1:1 채널의 참여자 집합은 생성 이후 바뀌지 않으므로 안전하다.
     *
     * <p><b>반드시 나가기 트랜잭션 안에서 호출해야 한다.</b> {@code pg_advisory_xact_lock} 은
     * 트랜잭션 종료 시 해제되므로 {@code REQUIRES_NEW} 로 분리하면 아무것도 막지 못한다.
     */
    @Transactional
    public void lockAndDeleteOneToOneMemberships(Long channelId) {
        List<UserDirectMessageChannelEntity> memberships =
                udmcJpaRepository.findAllByDirectMessageChannelId(channelId);

        // 참여자가 없으면 락 키를 만들 수 없고(ParticipantsLockKey 는 빈 목록을 거부한다) 지울 것도 없다.
        // 이번 변경 이전에 생긴 고아 채널이 여기에 걸릴 수 있어 예외 대신 조용히 넘긴다.
        if (memberships.isEmpty()) {
            return;
        }

        List<Long> participantIds = memberships.stream()
                .map(membership -> membership.getUser().getId())
                .toList();

        udmcJpaRepository.acquireChannelParticipantsLock(ParticipantsLockKey.of(participantIds));
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
