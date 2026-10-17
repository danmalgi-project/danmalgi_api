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

import java.time.Instant;
import java.util.Comparator;
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

    // 채팅 목록은 "최근 대화한 방이 위" 가 기본 기대다. last message 는 chat-server 소유라
    // DB ORDER BY 로는 못 풀고, getLastMessages 로 채운 뒤에야 정렬 키가 생긴다.
    // 시간이 없는 채널(메시지 0건, 또는 chat-server 가 created_at 을 안 준 방어적 케이스)은
    // 항상 뒤로 보낸다 — 방금 만든 빈 방이 대화 중인 방을 밀어내지 않게.
    // 동률·시간 없음 그룹 내부는 dmId 내림차순으로 고정해 같은 입력에 항상 같은 순서가 나오게 한다.
    private static final Comparator<DirectMessage> RECENT_CHANNEL_FIRST =
            Comparator.comparing(DirectMessageService::lastMessageTime,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(DirectMessage::getId, Comparator.reverseOrder());

    private static Instant lastMessageTime(DirectMessage channel) {
        LastMessage lastMessage = channel.getLastMessage();
        return lastMessage == null ? null : lastMessage.getCreatedAt();
    }

    public List<DirectMessage> getDirectMessageChannels(Long userId) {
        List<DirectMessage> channels = udmcService.getDirectMessagesByUserId(userId);
        // 채널 전체 id 를 한 번에 모아 단일 배치로 조회 (for 루프 안 채널당 호출 금지)
        List<Long> channelIds = channels.stream().map(DirectMessage::getId).toList();
        Map<Long, LastMessage> lastMessages = chatMessageClient.getLastMessages(channelIds);
        channels.forEach(channel -> channel.setLastMessage(lastMessages.get(channel.getId())));
        channels.forEach(this::applyPresignedUrls);
        // udmcService 가 주는 목록은 불변이라 in-place 정렬이 안 된다. 새 리스트로 반환한다.
        return channels.stream().sorted(RECENT_CHANNEL_FIRST).toList();
    }

    // 딥링크·방 안 재조회용 단건 조회라 last message 는 채우지 않는다 (chat-server 호출 없음).
    // 참여 여부는 getUserChannel 이 검사한다 — 미참여 방과 없는 방이 같은 NOT_FOUND 로 나가 방 존재를 드러내지 않는다.
    // 목록 조회와 같이 트랜잭션을 걸지 않는다. UDMC 연관관계가 EAGER 라 트랜잭션 밖에서도 lazy 예외가 없다.
    public DirectMessage getDirectMessageChannel(Long userId, Long channelId) {
        DirectMessage dm = udmcService.getDirectMessage(userId, channelId);
        applyPresignedUrls(dm);
        return dm;
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

    /**
     * 채널에서 나간다. 1:1 이면 방 자체를, 그룹이면 호출자의 참여 행만 지운다.
     *
     * <p>1:1 을 방까지 지우는 이유는 한쪽만 지우면 남은 사람에게 참여자 1명짜리 방이 남고,
     * 같은 상대와 다시 시작할 때 1:1 dedupe 에 걸리지 않아 중복 방이 생기기 때문이다.
     * 그룹은 반대로 혼자 남아도 유지해야 한다 — 남은 한 명이 계속 봐야 하는 방이다.
     * 단, 그룹도 마지막 한 명이 나가면 참여자 0명인 고아 채널이 되므로 그때는 방을 지운다.
     *
     * <p>1:1 여부는 참여자 수가 아니라 {@code isGroup} 으로 본다. 그룹에서 한 명이 나가면
     * 참여자가 2명이 되어 1:1 과 구분되지 않는다.
     *
     * <p>참여 검사는 {@code getUserChannel} 하나에 맡긴다 — 미참여 방과 없는 방이 같은
     * {@code NOT_FOUND} 로 나가 남의 방이 있는지 드러나지 않는다.
     *
     * <p>메시지(chat-server)와 채널 이미지(R2)는 지우지 않는다. 각각 삭제 수단이 없다.
     * dm-channel-model.md 8절에 빈틈으로 남겨 두었다.
     */
    @Transactional
    public void leaveChannel(Long userId, Long channelId) {
        UserDirectMessageChannelEntity membership = udmcService.getUserChannel(userId, channelId);
        DirectMessageChannelEntity channel = membership.getDirectMessageChannel();

        if (!Boolean.TRUE.equals(channel.getIsGroup())) {
            udmcService.lockAndDeleteOneToOneMemberships(channelId);
            dmcJpaRepository.delete(channel);
            return;
        }

        // 참여 행을 지우기 전에 세야 "나가면 0명이 되는가" 를 알 수 있다.
        long participants = udmcService.countParticipants(channelId);
        udmcService.leaveChannel(userId, channelId);

        if (participants <= 1) {
            dmcJpaRepository.delete(channel);
        }
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
