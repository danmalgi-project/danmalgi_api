package com.danmalgi.backend.relation.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danmalgi.backend.friendship.domain.model.FriendStatus;
import com.danmalgi.backend.friendship.repository.entity.FriendshipEntity;
import com.danmalgi.backend.friendship.repository.persistence.FriendshipJpaRepository;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.relation.domain.exception.RelationAccessDeniedException;
import com.danmalgi.backend.relation.domain.exception.RelationNotFoundException;
import com.danmalgi.backend.relation.domain.exception.RelationUpdateNotAllowedException;
import com.danmalgi.backend.relation.domain.exception.RelationUserNotFoundException;
import com.danmalgi.backend.relation.domain.model.Relation;
import com.danmalgi.backend.relation.domain.model.RelationStatus;
import com.danmalgi.backend.relation.repository.entity.RelationEntity;
import com.danmalgi.backend.relation.repository.persistence.RelationJpaRepository;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RelationService {
    private final RelationJpaRepository relationJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final FriendshipJpaRepository friendshipJpaRepository;
    private final R2Uploader r2Uploader;

    @Transactional
    public List<Relation> addRelationship(Long userId, String name, String tag) {
        UserEntity requesterEntity = userJpaRepository.findById(userId)
                .orElseThrow(() -> new RelationUserNotFoundException("requester user not found: id=" + userId));
        UserEntity receiverEntity = userJpaRepository.findByNameAndTag(name, tag)
                .orElseThrow(() -> new RelationUserNotFoundException(
                        "receiver user not found: name=" + name + ", tag=" + tag
                ));

        if (requesterEntity.getId().equals(receiverEntity.getId())) {
            throw new IllegalArgumentException("cannot create relationship with yourself");
        }

        relationJpaRepository.save(
                RelationEntity.of(requesterEntity, receiverEntity, RelationStatus.PENDING.forNumber())
        );

        List<Relation> result = relationJpaRepository.findAllByRequesterId(userId)
                .stream()
                .map(relationshipEntity -> new Relation(
                        relationshipEntity.getId(),
                        relationshipEntity.getReceiver().toDomainUser(),
                        relationshipEntity.getStatus()
                ))
                .collect(Collectors.toList());
        result.forEach(r -> r.getUser().applyPublicProfileImageUrl(r2Uploader));
        return result;
    }

    public List<Relation> getOutgoingRequest(Long userId) {
        List<Relation> result = relationJpaRepository
                .findAllByRequesterIdAndStatus(userId, RelationStatus.PENDING.forNumber())
                .stream()
                .map(relationshipEntity -> new Relation(
                        relationshipEntity.getId(),
                        relationshipEntity.getReceiver().toDomainUser(),
                        relationshipEntity.getStatus()
                ))
                .collect(Collectors.toList());
        result.forEach(r -> r.getUser().applyPublicProfileImageUrl(r2Uploader));
        return result;
    }

    public List<Relation> getIncomingRequests(Long userId) {
        List<Relation> result = relationJpaRepository
                .findAllByReceiverIdAndStatus(userId, RelationStatus.PENDING.forNumber())
                .stream()
                .map(relationshipEntity -> new Relation(
                        relationshipEntity.getId(),
                        relationshipEntity.getRequester().toDomainUser(),
                        relationshipEntity.getStatus()
                ))
                .collect(Collectors.toList());
        result.forEach(r -> r.getUser().applyPublicProfileImageUrl(r2Uploader));
        return result;
    }

    @Transactional
    public List<Relation> updateOutgoingRequest(Long userId, Long relationId, int status) {
        updateRelationshipStatus(userId, relationId, status, true);
        List<Relation> result = relationJpaRepository
                .findAllByRequesterIdAndStatus(userId, RelationStatus.PENDING.forNumber())
                .stream()
                .map(relationshipEntity -> new Relation(
                        relationshipEntity.getId(),
                        relationshipEntity.getReceiver().toDomainUser(),
                        relationshipEntity.getStatus()
                ))
                .collect(Collectors.toList());
        result.forEach(r -> r.getUser().applyPublicProfileImageUrl(r2Uploader));
        return result;
    }

    @Transactional
    public List<Relation> updateIncomingRequest(Long userId, Long relationId, int status) {
        RelationEntity relationEntity = updateRelationshipStatus(userId, relationId, status, false);

        if (status == RelationStatus.ACCEPT.forNumber()) {
            UserEntity requester = relationEntity.getRequester();
            UserEntity receiver = relationEntity.getReceiver();

            friendshipJpaRepository.saveAll(List.of(
                    FriendshipEntity.of(requester, receiver, FriendStatus.ACCEPT.getNumber()),
                    FriendshipEntity.of(receiver, requester, FriendStatus.ACCEPT.getNumber())
            ));
        }

        List<Relation> result = relationJpaRepository
                .findAllByReceiverIdAndStatus(userId, RelationStatus.PENDING.forNumber())
                .stream()
                .map(relationshipEntity -> new Relation(
                        relationshipEntity.getId(),
                        relationshipEntity.getRequester().toDomainUser(),
                        relationshipEntity.getStatus()
                ))
                .collect(Collectors.toList());
        result.forEach(r -> r.getUser().applyPublicProfileImageUrl(r2Uploader));
        return result;
    }

    private RelationEntity updateRelationshipStatus(Long userId, Long relationId, int status, boolean outgoing) {
        RelationEntity relationshipEntity = relationJpaRepository.findById(relationId)
                .orElseThrow(() -> new RelationNotFoundException("relationship not found: id=" + relationId));
        Long relationUserId = outgoing
                ? relationshipEntity.getRequester().getId()
                : relationshipEntity.getReceiver().getId();
        if (!relationUserId.equals(userId)) {
            throw new RelationAccessDeniedException("no permission to update this relationship");
        }
        if (relationshipEntity.getStatus() != RelationStatus.PENDING.forNumber()) {
            throw new RelationUpdateNotAllowedException("only pending relationship can be updated");
        }

        relationshipEntity.updateStatus(status);
        return relationJpaRepository.save(relationshipEntity);
    }
}
