package com.danmalgi.backend.dm.domain.model;

/**
 * 채널 하나의 안 읽은 메시지 수. chat-server 가 계산해 준다.
 *
 * @param count     본인이 보낸 메시지를 제외한 안 읽은 수. 최대 99
 * @param truncated true 면 실제 수가 99 를 넘는다 (클라이언트는 "99+" 로 표시)
 */
public record UnreadCount(int count, boolean truncated) {
}
