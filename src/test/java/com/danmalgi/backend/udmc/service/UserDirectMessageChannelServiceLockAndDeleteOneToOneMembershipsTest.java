package com.danmalgi.backend.udmc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.domain.ParticipantsLockKey;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceLockAndDeleteOneToOneMembershipsTest {

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

    private UserDirectMessageChannelEntity membershipOf(Long userId) {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);

        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder()
                .id(CHANNEL_ID)
                .isGroup(false)
                .build();

        return UserDirectMessageChannelEntity.of(user, channel, "상대방");
    }

    @Test
    void lockAndDeleteOneToOneMemberships_참여자_전원의_행을_삭제한다() {
        List<UserDirectMessageChannelEntity> memberships = List.of(membershipOf(1L), membershipOf(2L));
        when(udmcJpaRepository.findAllByDirectMessageChannelId(CHANNEL_ID)).thenReturn(memberships);

        service.lockAndDeleteOneToOneMemberships(CHANNEL_ID);

        verify(udmcJpaRepository).deleteAll(memberships);
    }

    @Test
    void lockAndDeleteOneToOneMemberships_삭제보다_advisory_lock을_먼저_획득한다() {
        // membershipOf() 안에서 다른 mock 을 스터빙하므로 thenReturn() 인자 자리에서 호출하면
        // 바깥 스터빙이 닫히기 전에 중첩 스터빙이 시작돼 UnfinishedStubbingException 이 난다.
        List<UserDirectMessageChannelEntity> memberships = List.of(membershipOf(1L), membershipOf(2L));
        when(udmcJpaRepository.findAllByDirectMessageChannelId(CHANNEL_ID)).thenReturn(memberships);

        service.lockAndDeleteOneToOneMemberships(CHANNEL_ID);

        InOrder inOrder = inOrder(udmcJpaRepository);
        inOrder.verify(udmcJpaRepository).acquireChannelParticipantsLock(anyLong());
        inOrder.verify(udmcJpaRepository).deleteAll(anyIterable());
    }

    @Test
    void lockAndDeleteOneToOneMemberships_채널_생성_dedupe와_같은_락_키를_쓴다() {
        List<UserDirectMessageChannelEntity> memberships = List.of(membershipOf(2L), membershipOf(1L));
        when(udmcJpaRepository.findAllByDirectMessageChannelId(CHANNEL_ID)).thenReturn(memberships);

        service.lockAndDeleteOneToOneMemberships(CHANNEL_ID);

        // 키가 다르면 생성 측과 직렬화되지 않아 "곧 사라질 방" 이 반환될 수 있다.
        ArgumentCaptor<Long> keyCaptor = ArgumentCaptor.forClass(Long.class);
        verify(udmcJpaRepository).acquireChannelParticipantsLock(keyCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo(ParticipantsLockKey.of(List.of(1L, 2L)));
    }

    @Test
    void lockAndDeleteOneToOneMemberships_참여자가_없으면_락도_삭제도_하지_않는다() {
        when(udmcJpaRepository.findAllByDirectMessageChannelId(CHANNEL_ID)).thenReturn(List.of());

        service.lockAndDeleteOneToOneMemberships(CHANNEL_ID);

        verify(udmcJpaRepository, never()).acquireChannelParticipantsLock(anyLong());
        verify(udmcJpaRepository, never()).deleteAll(anyIterable());
    }
}
