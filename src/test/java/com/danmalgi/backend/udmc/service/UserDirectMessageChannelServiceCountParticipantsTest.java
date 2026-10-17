package com.danmalgi.backend.udmc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceCountParticipantsTest {

    private static final Long CHANNEL_ID = 10L;

    @Mock
    private UserDirectMessageChannelJpaRepository udmcJpaRepository;
    @Mock
    private DirectMessageChannelJpaRepository dmcJpaRepository;
    @Mock
    private UserJpaRepository userJpaRepository;

    private UserDirectMessageChannelService service;

    @BeforeEach
    void setUp() {
        service = new UserDirectMessageChannelService(udmcJpaRepository, dmcJpaRepository, userJpaRepository);
    }

    @Test
    void countParticipants_채널의_참여자_수를_그대로_반환한다() {
        when(udmcJpaRepository.countByDirectMessageChannelId(CHANNEL_ID)).thenReturn(3L);

        assertThat(service.countParticipants(CHANNEL_ID)).isEqualTo(3L);
    }

    @Test
    void countParticipants_참여자가_없으면_0을_반환한다() {
        when(udmcJpaRepository.countByDirectMessageChannelId(CHANNEL_ID)).thenReturn(0L);

        assertThat(service.countParticipants(CHANNEL_ID)).isZero();
    }
}
