package com.danmalgi.backend.udmc.repository.entity;

import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.user.repository.entity.UserEntity;

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

    public static UserDirectMessageChannelEntity of(
            UserEntity user,
            DirectMessageChannelEntity directMessageChannel,
            String channelName
    ) {
        return new UserDirectMessageChannelEntity(
                null,
                user,
                directMessageChannel,
                channelName
        );
    }

    public void updateChannelName(String channelName) {
        this.channelName = channelName;
    }
}
