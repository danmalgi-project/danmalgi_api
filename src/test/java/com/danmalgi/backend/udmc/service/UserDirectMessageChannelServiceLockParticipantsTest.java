package com.danmalgi.backend.udmc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceLockParticipantsTest {

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

    private UserDirectMessageChannelEntity membershipOf(Long userId, String name) {
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder()
                .id(CHANNEL_ID)
                .isGroup(false)
                .build();
        UserEntity user = UserEntity.from(new User(userId, name + "@a.com", name, "0000" + userId, null, 0));

        return UserDirectMessageChannelEntity.of(user, channel, name);
    }

    @Test
    void lockParticipants_채널의_참여자를_그대로_반환한다() {
        List<UserDirectMessageChannelEntity> participants =
                List.of(membershipOf(1L, "Alice"), membershipOf(2L, "Bob"));
        when(udmcJpaRepository.lockAllByDirectMessageChannelId(CHANNEL_ID)).thenReturn(participants);

        assertThat(service.lockParticipants(CHANNEL_ID)).isEqualTo(participants);
    }

    @Test
    void lockParticipants_참여자가_없으면_빈_목록을_반환한다() {
        when(udmcJpaRepository.lockAllByDirectMessageChannelId(CHANNEL_ID)).thenReturn(List.of());

        assertThat(service.lockParticipants(CHANNEL_ID)).isEmpty();
    }

    /**
     * 락 없는 조회로 갈아타면 나가기 직렬화가 통째로 사라지는데 단위 테스트로는 티가 안 난다.
     * findAllByDirectMessageChannelId 는 채널 목록 조회·푸시 대상 조회가 쓰는 읽기 경로라
     * 반대로 거기에 락이 번져서도 안 된다. 그래서 호출 자체를 고정한다.
     */
    @Test
    void lockParticipants_락_없는_조회_메서드를_쓰지_않는다() {
        when(udmcJpaRepository.lockAllByDirectMessageChannelId(CHANNEL_ID)).thenReturn(List.of());

        service.lockParticipants(CHANNEL_ID);

        verify(udmcJpaRepository).lockAllByDirectMessageChannelId(CHANNEL_ID);
        verify(udmcJpaRepository, never()).findAllByDirectMessageChannelId(any());
    }
}
