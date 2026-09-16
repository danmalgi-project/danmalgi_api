package com.danmalgi.backend.dm.repository.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;

@Repository
public interface DirectMessageChannelJpaRepository extends JpaRepository<DirectMessageChannelEntity, Long> {

}
