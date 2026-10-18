package com.danmalgi.backend.udmc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
class UserDirectMessageChannelServiceDeleteMembershipsTest {

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
                .isGroup(true)
                .build();
        UserEntity user = UserEntity.from(new User(userId, name + "@a.com", name, "0000" + userId, null, 0));

        return UserDirectMessageChannelEntity.of(user, channel, name);
    }

    @Test
    void deleteMemberships_넘겨받은_행만_삭제한다() {
        List<UserDirectMessageChannelEntity> memberships =
                List.of(membershipOf(1L, "Alice"), membershipOf(2L, "Bob"));

        service.deleteMemberships(memberships);

        verify(udmcJpaRepository).deleteAll(memberships);
        verify(udmcJpaRepository, never()).delete(any());
    }

    /**
     * 그룹에서 혼자 나갈 때 쓰는 모양이다. 전원분을 지우면 남은 사람의 방이 사라진다.
     */
    @Test
    void deleteMemberships_한_건만_넘기면_그_행만_삭제한다() {
        UserDirectMessageChannelEntity mine = membershipOf(1L, "Alice");

        service.deleteMemberships(List.of(mine));

        verify(udmcJpaRepository).deleteAll(List.of(mine));
    }

    /**
     * 방을 지울지는 호출자(DirectMessageService.leaveChannel)가 판단한다.
     * 여기서 DMC 를 같이 지우면 그룹에서 혼자 나갈 때 남은 사람의 방까지 없어진다.
     */
    @Test
    void deleteMemberships_채널행은_건드리지_않는다() {
        service.deleteMemberships(List.of(membershipOf(1L, "Alice")));

        verifyNoInteractions(dmcJpaRepository);
    }

    @Test
    void deleteMemberships_빈_목록이면_지울_것이_없다() {
        service.deleteMemberships(List.of());

        verify(udmcJpaRepository).deleteAll(List.of());
        verifyNoInteractions(dmcJpaRepository);
    }
}
