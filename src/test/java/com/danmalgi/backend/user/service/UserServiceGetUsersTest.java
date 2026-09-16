package com.danmalgi.backend.user.service;

import static com.danmalgi.backend.user.fixture.UserFixture.createUserEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.user.domain.exception.UserNotFoundException;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceGetUsersTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private R2Uploader r2Uploader;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getUsers_userIds로_조회하면_해당_유저_리스트를_반환한다() {
        List<Long> ids = List.of(1L, 2L, 3L);
        when(userJpaRepository.findAllById(ids)).thenReturn(List.of(
                createUserEntity(1L, "유저1", "0001"),
                createUserEntity(2L, "유저2", "0002"),
                createUserEntity(3L, "유저3", "0003")
        ));

        List<User> result = userService.getUsers(ids);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(User::getId).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void getUsers_DB_조회_결과를_Redis에_저장한다() {
        List<Long> ids = List.of(1L, 2L);
        when(userJpaRepository.findAllById(ids)).thenReturn(List.of(
                createUserEntity(1L, "유저1", "0001"),
                createUserEntity(2L, "유저2", "0002")
        ));

        userService.getUsers(ids);

        verify(valueOperations).multiSet(anyMap());
        verify(redisTemplate, atLeastOnce()).expire(anyString(), any());
    }

    @Test
    void getUsers_존재하지_않는_userId가_포함되면_예외를_던진다() {
        List<Long> ids = List.of(1L, 2L, 999L);
        when(userJpaRepository.findAllById(ids)).thenReturn(List.of(
                createUserEntity(1L, "유저1", "0001"),
                createUserEntity(2L, "유저2", "0002")
        ));

        assertThatThrownBy(() -> userService.getUsers(ids))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUsers_빈_리스트_입력시_빈_리스트를_반환한다() {
        when(userJpaRepository.findAllById(List.of())).thenReturn(List.of());

        List<User> result = userService.getUsers(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void getUsers_캐시에_담기는_User의_profileImageUrl은_public_URL이다() {
        UserEntity entity = createUserEntity(1L, "유저1", "0001");
        entity.updateProfileImageUrl("profiles/1/a.webp");
        when(userJpaRepository.findAllById(List.of(1L))).thenReturn(List.of(entity));
        when(r2Uploader.toPublicUrl("profiles/1/a.webp"))
                .thenReturn("https://cdn.example.com/profiles/1/a.webp");

        userService.getUsers(List.of(1L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(valueOperations).multiSet(captor.capture());

        User cached = (User) captor.getValue().get("user:1");
        assertThat(cached.getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/1/a.webp");
    }

    @Test
    void getUsers_public_URL_조립이_캐시_write보다_먼저_일어난다() {
        // 이전 설계(#21)는 정반대였다 — multiSet 이 먼저 일어나 캐시에 raw key 가 들어갔고,
        // 그래서 chat/webrtc 가 캐시를 직접 읽을 때 path 를 보게 되는 것이 이슈 #30 이다.
        UserEntity entity = createUserEntity(1L, "유저1", "0001");
        entity.updateProfileImageUrl("profiles/1/a.webp");
        when(userJpaRepository.findAllById(List.of(1L))).thenReturn(List.of(entity));
        when(r2Uploader.toPublicUrl("profiles/1/a.webp"))
                .thenReturn("https://cdn.example.com/profiles/1/a.webp");

        InOrder inOrder = inOrder(r2Uploader, valueOperations);

        userService.getUsers(List.of(1L));

        inOrder.verify(r2Uploader).toPublicUrl("profiles/1/a.webp");
        inOrder.verify(valueOperations).multiSet(anyMap());
    }
}
