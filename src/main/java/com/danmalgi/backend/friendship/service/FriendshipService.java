package com.danmalgi.backend.friendship.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danmalgi.backend.friendship.domain.model.FriendStatus;
import com.danmalgi.backend.friendship.domain.model.Friendship;
import com.danmalgi.backend.friendship.repository.entity.FriendshipEntity;
import com.danmalgi.backend.friendship.repository.persistence.FriendshipJpaRepository;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FriendshipService {
    private final FriendshipJpaRepository friendshipJpaRepository;
    private final R2Uploader r2Uploader;

    public List<Friendship> getFriends(Long userId) {
        List<Friendship> friendships = friendshipJpaRepository.findAllByUserId(userId)
                .stream()
                .map(friendshipEntity -> Friendship.builder()
                    .id(friendshipEntity.getId())
                    .friend(friendshipEntity.getFriend().toDomainUser())
                    .status(FriendStatus.getFriendStatus(friendshipEntity.getStatus()))
                    .build()
                )
                .collect(Collectors.toList());
        friendships.forEach(f -> f.getFriend().applyPublicProfileImageUrl(r2Uploader));
        return friendships;
    }

    @Transactional
    public List<Friendship> updateFriend(Long userId, Long friendshipId, int status) {
        FriendshipEntity friendshipEntity = friendshipJpaRepository.findByIdAndUserId(friendshipId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "friendship not found: id=" + friendshipId + ", userId=" + userId
                ));

        friendshipEntity.updateStatus(status);
        friendshipJpaRepository.save(friendshipEntity);

        return getFriends(userId);
    }
}
