package com.danmalgi.backend.udmc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.domain.ParticipantsLockKey;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceLockAndFindOneToOneChannelIdTest {

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
    void lockAndFindOneToOneChannelId_조회보다_advisory_lock을_먼저_획득한다() {
        service.lockAndFindOneToOneChannelId(List.of(1L, 2L));

        InOrder inOrder = inOrder(udmcJpaRepository);
        inOrder.verify(udmcJpaRepository).acquireChannelParticipantsLock(anyLong());
        inOrder.verify(udmcJpaRepository).findChannelIdsByExactParticipants(anyList(), anyLong());
    }

    @Test
    void lockAndFindOneToOneChannelId_락_키는_참여자_순서와_무관하다() {
        service.lockAndFindOneToOneChannelId(List.of(1L, 2L));
        service.lockAndFindOneToOneChannelId(List.of(2L, 1L));

        ArgumentCaptor<Long> keyCaptor = ArgumentCaptor.forClass(Long.class);
        verify(udmcJpaRepository, org.mockito.Mockito.times(2))
                .acquireChannelParticipantsLock(keyCaptor.capture());

        assertThat(keyCaptor.getAllValues().get(0))
                .isEqualTo(keyCaptor.getAllValues().get(1))
                .isEqualTo(ParticipantsLockKey.of(List.of(1L, 2L)));
    }

    @Test
    void lockAndFindOneToOneChannelId_참여자_수를_participantCount로_전달한다() {
        service.lockAndFindOneToOneChannelId(List.of(1L, 2L));

        verify(udmcJpaRepository).findChannelIdsByExactParticipants(List.of(1L, 2L), 2L);
    }

    @Test
    void lockAndFindOneToOneChannelId_조회_결과가_여러건이면_가장_앞의_채널_id를_반환한다() {
        when(udmcJpaRepository.findChannelIdsByExactParticipants(List.of(1L, 2L), 2L))
                .thenReturn(List.of(10L, 15L));

        Optional<Long> result = service.lockAndFindOneToOneChannelId(List.of(1L, 2L));

        assertThat(result).contains(10L);
    }

    @Test
    void lockAndFindOneToOneChannelId_조회_결과가_없으면_빈_Optional을_반환한다() {
        when(udmcJpaRepository.findChannelIdsByExactParticipants(List.of(1L, 2L), 2L))
                .thenReturn(List.of());

        Optional<Long> result = service.lockAndFindOneToOneChannelId(List.of(1L, 2L));

        assertThat(result).isEmpty();
    }
}
