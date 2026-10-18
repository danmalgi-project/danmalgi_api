package com.danmalgi.backend.udmc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danmalgi.backend.dm.domain.model.DirectMessage;
import com.danmalgi.backend.dm.repository.entity.DirectMessageChannelEntity;
import com.danmalgi.backend.dm.repository.persistence.DirectMessageChannelJpaRepository;
import com.danmalgi.backend.udmc.repository.entity.UserDirectMessageChannelEntity;
import com.danmalgi.backend.udmc.repository.persistence.UserDirectMessageChannelJpaRepository;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectMessageChannelServiceFindDirectMessageTest {

    private static final Long USER_ID = 1L;
    private static final Long FRIEND_ID = 2L;
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
    void findDirectMessage_참여중이면_DirectMessage를_반환한다() {
        DirectMessageChannelEntity channel = DirectMessageChannelEntity.builder()
                .id(CHANNEL_ID)
                .isGroup(false)
                .build();
        UserEntity me = UserEntity.from(new User(USER_ID, "a@a.com", "Alice", "00001", null, 0));
        UserEntity friend = UserEntity.from(new User(FRIEND_ID, "b@b.com", "Bob", "00002", null, 0));
        UserDirectMessageChannelEntity myMembership = UserDirectMessageChannelEntity.of(me, channel, "Bob");
        UserDirectMessageChannelEntity friendMembership =
                UserDirectMessageChannelEntity.of(friend, channel, "Alice");

        when(udmcJpaRepository.findByUser_IdAndDirectMessageChannel_Id(USER_ID, CHANNEL_ID))
                .thenReturn(Optional.of(myMembership));
        when(udmcJpaRepository.findAllByDirectMessageChannelId(CHANNEL_ID))
                .thenReturn(List.of(myMembership, friendMembership));

        Optional<DirectMessage> result = service.findDirectMessage(USER_ID, CHANNEL_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(CHANNEL_ID);
        assertThat(result.get().getChannelName()).isEqualTo("Bob");
        assertThat(result.get().getUsers())
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    /**
     * 예외를 던지지 않는 것이 이 메서드의 존재 이유다. dedupe 로 찾은 채널이 재로드 전에
     * 지워졌을 때 생성 요청이 NOT_FOUND 로 끝나지 않고 새 방을 만들 수 있어야 한다.
     */
    @Test
    void findDirectMessage_참여행이_없으면_빈_Optional을_반환한다() {
        when(udmcJpaRepository.findByUser_IdAndDirectMessageChannel_Id(USER_ID, CHANNEL_ID))
                .thenReturn(Optional.empty());

        assertThatCode(() -> assertThat(service.findDirectMessage(USER_ID, CHANNEL_ID)).isEmpty())
                .doesNotThrowAnyException();

        verify(udmcJpaRepository, never()).findAllByDirectMessageChannelId(any());
    }
}
