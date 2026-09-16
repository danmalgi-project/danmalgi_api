package com.danmalgi.backend.chat.client;

import com.danmalgi.backend.chat.client.mapper.ChatMessageMapper;
import com.danmalgi.backend.dm.domain.model.LastMessage;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.ChannelLastMessage;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.GetLastMessagesRequest;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreProto.GetLastMessagesResponse;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreServiceGrpc.ChatStoreServiceBlockingStub;

import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageGrpcClient implements ChatMessageClient {

    private final ChatStoreServiceBlockingStub chatStoreServiceBlockingStub;

    @Override
    public Map<Long, LastMessage> getLastMessages(List<Long> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 채널 전체를 단일 요청으로 전송한다(반복문 내 원격 호출 금지 = 정확히 1콜).
        GetLastMessagesRequest request = GetLastMessagesRequest.newBuilder()
                .addAllChannelIds(channelIds)
                .build();

        try {
            GetLastMessagesResponse response = chatStoreServiceBlockingStub.getLastMessages(request);
            return response.getLastMessagesList().stream()
                    .collect(Collectors.toMap(
                            ChannelLastMessage::getChannelId,
                            ChatMessageMapper::toDomainLastMessage
                    ));
        } catch (StatusRuntimeException e) {
            // chat-server 실패는 도메인 예외가 아니라 graceful degrade — 마지막 메시지 없이 목록만 반환.
            log.warn("chat-server 마지막 메시지 배치 조회 실패 - 빈 결과로 degrade. channelIds={}", channelIds, e);
            return Collections.emptyMap();
        }
    }
}
