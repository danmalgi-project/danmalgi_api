package com.danmalgi.backend.dm.domain.model;

import java.time.Instant;
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
    // 목록 응답에서만 채운다. 그 외 응답에서는 기본값(0/false)이고 proto 로 나가지 않는다.
    private int unreadCount;
    private boolean unreadCountTruncated;
    // 호출자 본인의 참여 시각. 안 읽은 수 조회에만 쓰고 클라이언트로 내보내지 않는다.
    private Instant joinedAt;

    // 목록 전용 필드(unreadCount, unreadCountTruncated, joinedAt)를 기본값으로 두는 생성자.
    // 단건·생성·수정 응답은 이 필드들을 쓰지 않는다.
    public DirectMessage(
            Long id,
            String channelName,
            Boolean isGroup,
            String channelImageUrl,
            List<User> users,
            LastMessage lastMessage
    ) {
        this(id, channelName, isGroup, channelImageUrl, users, lastMessage, 0, false, null);
    }

    public void applyPresignedChannelImageUrl(R2Uploader r2Uploader) {
        if (channelImageUrl == null || channelImageUrl.isBlank()) {
            return;
        }
        this.channelImageUrl = r2Uploader.generatePresignedUrl(channelImageUrl);
    }
}
