package com.danmalgi.backend.chat.client.config;

import com.danmalgi.backend.chat.client.interceptor.JwtForwardingClientInterceptor;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreServiceGrpc;
import com.danmalgi.backend.internal.chat_store.v1.ChatStoreServiceGrpc.ChatStoreServiceBlockingStub;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * chat-server 아웃바운드 gRPC 채널/스텁 설정.
 * spring-grpc 스타터가 제공하는 {@link GrpcChannelFactory} 로 "chat-server" 채널을 생성한다
 * (주소는 application.yaml spring.grpc.client.channels.chat-server.address).
 */
@Configuration
public class ChatClientConfig {

    private static final String CHAT_SERVER_CHANNEL = "chat-server";

    @Bean
    public ChatStoreServiceBlockingStub chatStoreServiceBlockingStub(GrpcChannelFactory channelFactory) {
        // 클라이언트 원본 JWT를 chat-server 로 전달하도록 아웃바운드 인증 인터셉터를 부착한다.
        return ChatStoreServiceGrpc.newBlockingStub(channelFactory.createChannel(CHAT_SERVER_CHANNEL))
                .withInterceptors(new JwtForwardingClientInterceptor());
    }
}
