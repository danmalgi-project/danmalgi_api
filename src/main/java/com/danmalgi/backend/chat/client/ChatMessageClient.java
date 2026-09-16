package com.danmalgi.backend.chat.client;

import com.danmalgi.backend.dm.domain.model.LastMessage;

import java.util.List;
import java.util.Map;

/**
 * chat-server(별도 서비스)로 나가는 아웃바운드 어댑터.
 * 채널 ID 목록을 받아 각 채널의 마지막 메시지를 배치 조회한다.
 */
public interface ChatMessageClient {

    /**
     * 채널 ID 목록 전체를 단일 배치로 조회한다(채널당 호출 아님).
     *
     * @param channelIds 조회 대상 채널 ID 목록
     * @return channelId → 마지막 메시지 맵. 마지막 메시지가 없는 채널은 키 자체가 없다.
     */
    Map<Long, LastMessage> getLastMessages(List<Long> channelIds);
}
