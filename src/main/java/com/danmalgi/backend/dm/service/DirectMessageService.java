package com.danmalgi.backend.dm.service;

import com.danmalgi.backend.chat.client.ChatMessageClient;
import com.danmalgi.backend.dm.domain.exception.NotGroupChannelException;
import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.global.infrastructure.image.ImageProcessor;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.service.UserDirectMessageChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DirectMessageService {
    private final DirectMessageChannelJpaRepository dmcJpaRepository;
    private final UserDirectMessageChannelService udmcService;
    private final R2Uploader r2Uploader;
    private final ImageProcessor imageProcessor;
    private final ChatMessageClient chatMessageClient;

    public List<DirectMessage> getDirectMessageChannels(Long userId) {
        List<DirectMessage> channels = udmcService.getDirectMessagesByUserId(userId);
        // 채널 전체 id 를 한 번에 모아 단일 배치로 조회 (for 루프 안 채널당 호출 금지)
        List<Long> channelIds = channels.stream().map(DirectMessage::getId).toList();
        Map<Long, LastMessage> lastMessages = chatMessageClient.getLastMessages(channelIds);
        channels.forEach(channel -> channel.setLastMessage(lastMessages.get(channel.getId())));
        channels.forEach(this::applyPresignedUrls);
        return channels;
    }

    @Transactional
    public DirectMessage createChannel(Long userId, List<Long> friendIds) {
        // 1:1 만 dedupe 한다. 같은 멤버로 주제별 그룹방을 여러 개 만드는 것은 정상 시나리오다.
        if (friendIds.size() == 1) {
            Optional<Long> existingChannelId =
                    udmcService.lockAndFindOneToOneChannelId(List.of(userId, friendIds.get(0)));

            if (existingChannelId.isPresent()) {
                DirectMessage existing = udmcService.getDirectMessage(userId, existingChannelId.get());
                applyPresignedUrls(existing);
                return existing;
            }
        }

        DirectMessageChannelEntity dmcEntity = dmcJpaRepository.save(DirectMessageChannelEntity
                .builder()
                .isGroup(friendIds.size() > 1)
                .build());

        DirectMessage dm = udmcService.createUserChannel(userId, friendIds, dmcEntity.getId());
        applyPresignedUrls(dm);
        return dm;
    }

    @Transactional
    public DirectMessage updateChannel(Long userId, Long channelId, String channelName, byte[] image, String extension) {
        UserDirectMessageChannelEntity membership = udmcService.getUserChannel(userId, channelId);
        DirectMessageChannelEntity channel = membership.getDirectMessageChannel();

        validateGroupChannel(channel, channelId);

        boolean hasChannelName = channelName != null && !channelName.isBlank();
        boolean hasImage = image != null && image.length > 0;

        if (!hasChannelName && !hasImage) {
            throw new IllegalArgumentException("channel_name or image must be provided");
        }

        if (hasChannelName) {
            udmcService.updateChannelName(channelId, channelName);
        }

        if (hasImage) {
            updateChannelImage(channelId, channel, image);
        }

        DirectMessage dm = udmcService.getDirectMessage(userId, channelId);
        applyPresignedUrls(dm);
        return dm;
    }

    @Transactional
    public void leaveChannel(Long userId, Long channelId) {
        udmcService.leaveChannel(userId, channelId);
    }

    private void validateGroupChannel(DirectMessageChannelEntity entity, Long channelId) {
        if (!Boolean.TRUE.equals(entity.getIsGroup())) {
            throw new NotGroupChannelException("channel is not a group: " + channelId);
        }
    }

    private void updateChannelImage(Long channelId, DirectMessageChannelEntity entity, byte[] image) {
        byte[] webp = imageProcessor.toWebp(image);
        String key = r2Uploader.uploadChannelImage(channelId, webp, "webp");
        entity.updateChannelImageUrl(key);
        dmcJpaRepository.save(entity);
    }

    private void applyPresignedUrls(DirectMessage dm) {
        dm.applyPresignedChannelImageUrl(r2Uploader);
        if (dm.getUsers() != null) {
            dm.getUsers().forEach(u -> u.applyPublicProfileImageUrl(r2Uploader));
        }
    }
}
