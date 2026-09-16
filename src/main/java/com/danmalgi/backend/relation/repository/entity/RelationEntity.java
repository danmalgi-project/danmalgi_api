package com.danmalgi.backend.relation.repository.entity;

import com.danmalgi.backend.user.repository.entity.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "relations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "requester_id")
    private UserEntity requester;

    @ManyToOne
    @JoinColumn(name = "receiver_id")
    private UserEntity receiver;

    @Column(nullable = false)
    private int status;

    @Column(nullable = false)
    private LocalDateTime requestAt;

    public static RelationEntity of(UserEntity requester, UserEntity receiver, int status) {
        RelationEntity relationshipEntity = new RelationEntity();
        relationshipEntity.requester = requester;
        relationshipEntity.receiver = receiver;
        relationshipEntity.status = status;
        relationshipEntity.requestAt = LocalDateTime.now();
        return relationshipEntity;
    }

    public void updateStatus(int status) {
        this.status = status;
    }
}
