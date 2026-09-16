package com.danmalgi.backend.dm.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.danmalgi.backend.chat.client.ChatMessageClient;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.global.infrastructure.image.ImageProcessor;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.udmc.service.UserDirectMessageChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceLeaveChannelTest {

    @Mock
    private DirectMessageChannelJpaRepository dmcJpaRepository;
    @Mock
    private UserDirectMessageChannelService udmcService;
    @Mock
    private R2Uploader r2Uploader;
    @Mock
    private ImageProcessor imageProcessor;
    @Mock
    private ChatMessageClient chatMessageClient;

    private DirectMessageService directMessageService;

    @BeforeEach
    void setUp() {
        directMessageService = new DirectMessageService(dmcJpaRepository, udmcService, r2Uploader, imageProcessor, chatMessageClient);
    }

    @Test
    void leaveChannel_delegatesToMembershipService() {
        directMessageService.leaveChannel(1L, 10L);

        verify(udmcService).leaveChannel(1L, 10L);
        verifyNoInteractions(dmcJpaRepository, r2Uploader, imageProcessor);
    }
}
