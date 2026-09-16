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

    @Transactional
    public void leaveChannel(Long userId, Long channelId) {
        UserDirectMessageChannelEntity membership = getUserChannel(userId, channelId);

        // Keep the channel row even when the last member leaves.
        // Message and asset cleanup are outside this issue's scope.
        udmcJpaRepository.delete(membership);
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
