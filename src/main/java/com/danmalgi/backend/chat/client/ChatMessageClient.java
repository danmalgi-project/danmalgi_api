package com.danmalgi.backend.chat.client;

import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.dm.domain.model.UnreadCount;
import com.danmalgi.backend.dm.domain.model.UnreadCountQuery;

import java.util.List;
import java.util.Map;

/**
 * chat-server(별도 서비스)로 나가는 아웃바운드 어댑터.
 * 채널 목록에 붙일 메타(마지막 메시지, 안 읽은 수)를 배치 조회한다.
 */
public interface ChatMessageClient {

    /**
     * 채널 ID 목록 전체를 단일 배치로 조회한다(채널당 호출 아님).
     *
     * @param channelIds 조회 대상 채널 ID 목록
     * @return channelId → 마지막 메시지 맵. 마지막 메시지가 없는 채널은 키 자체가 없다.
     */
    Map<Long, LastMessage> getLastMessages(List<Long> channelIds);

    /**
     * 유저의 채널별 안 읽은 메시지 수를 단일 배치로 조회한다(채널당 호출 아님).
     *
     * @param userId  읽은 위치의 주인. 호출자 본인이다.
     * @param queries 조회 대상 채널과 참여 시각
     * @return channelId → 안 읽은 수 맵. chat-server 가 조회에 실패한 채널은 키 자체가 없고, 호출자는 0 으로 다룬다.
     */
    Map<Long, UnreadCount> getUnreadCounts(Long userId, List<UnreadCountQuery> queries);
}
