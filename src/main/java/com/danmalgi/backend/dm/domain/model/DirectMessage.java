package com.danmalgi.backend.dm.domain.model;

import java.util.List;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.user.domain.model.User;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DirectMessage {
    private Long id;
    private String channelName;
    private Boolean isGroup;
    private String channelImageUrl;
    private List<User> users;
    private LastMessage lastMessage;

    public void applyPresignedChannelImageUrl(R2Uploader r2Uploader) {
        if (channelImageUrl == null || channelImageUrl.isBlank()) {
            return;
        }
        this.channelImageUrl = r2Uploader.generatePresignedUrl(channelImageUrl);
    }
}
