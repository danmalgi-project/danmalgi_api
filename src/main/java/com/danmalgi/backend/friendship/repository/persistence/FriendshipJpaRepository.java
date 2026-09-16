package com.danmalgi.backend.friendship.repository.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.danmalgi.backend.friendship.repository.entity.FriendshipEntity;

@Repository
public interface FriendshipJpaRepository extends JpaRepository<FriendshipEntity, Long> {
    List<FriendshipEntity> findAllByUserId(Long userId);
    Optional<FriendshipEntity> findByIdAndUserId(Long friendshipId, Long userId);
}
