package com.danmalgi.backend.relation.repository.persistence;

import com.danmalgi.backend.relation.repository.entity.RelationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelationJpaRepository extends JpaRepository<RelationEntity, Long> {
    List<RelationEntity> findAllByRequesterId(Long requesterId);
    List<RelationEntity> findAllByRequesterIdAndStatus(Long requesterId, int status);
    List<RelationEntity> findAllByReceiverIdAndStatus(Long receiverId, int status);
}
