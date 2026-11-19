package com.danmalgi.backend.udmc.repository.entity;

import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.user.repository.entity.UserEntity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "user_direct_message_channels",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "direct_message_channel_id"})
    }   
)
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserDirectMessageChannelEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    
    @ManyToOne
    @JoinColumn(name = "direct_message_channel_id", nullable = false)
    private DirectMessageChannelEntity directMessageChannel;

    @Column(nullable = false, length = 32)
    private String channelName;

    // 이 참여가 시작된 시각. chat-server 가 안 읽은 수를 셀 때 이 시각 이전 메시지를 뺀다.
    // 나가기가 행을 삭제하므로 재참여는 새 행이 되고, 이 값도 새 참여 시각이 된다.
    // nullable 인 이유: ddl-auto: update 는 행이 있는 테이블에 NOT NULL 컬럼을 추가하지 못한다.
    // 컬럼 추가 전 행은 null 로 남는다 (지금은 채널 생성 시각과 같아 direct_message_channels.created_at 으로 백필 가능).
    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private Instant joinedAt;

    public static UserDirectMessageChannelEntity of(
            UserEntity user,
            DirectMessageChannelEntity directMessageChannel,
            String channelName
    ) {
        return new UserDirectMessageChannelEntity(
                null,
                user,
                directMessageChannel,
                channelName,
                null
        );
    }

    public void updateChannelName(String channelName) {
        this.channelName = channelName;
    }
}
