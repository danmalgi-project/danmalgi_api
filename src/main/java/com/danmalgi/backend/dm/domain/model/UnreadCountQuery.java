package com.danmalgi.backend.dm.domain.model;

import java.time.Instant;

/**
 * 안 읽은 수 조회 대상 채널 하나.
 *
 * @param channelId 채널 ID
 * @param joinedAt  호출자가 이 채널에 참여한 시각. chat-server 는 이 시각 이전 메시지를 세지 않는다.
 *                  컬럼 추가 전에 만들어진 참여 행은 null 이고, null 이면 chat-server 로 보내지 않는다.
 */
public record UnreadCountQuery(Long channelId, Instant joinedAt) {
}
