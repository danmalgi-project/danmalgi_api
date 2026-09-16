package com.danmalgi.backend.user.service;

import static com.danmalgi.backend.user.fixture.UserFixture.createUserEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.user.domain.exception.UserNotFoundException;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceGetUserTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private R2Uploader r2Uploader;

    @Test
    void getUser_존재하는_유저_조회_성공() {
        UserEntity entity = createUserEntity(1L, "홍길동", "0001");
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(entity));

        User user = userService.getUser(1L);

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getTag()).isEqualTo("0001");
        assertThat(user.getEmail()).isEqualTo("test@test.com");
    }

    @Test
    void getUser_존재하지_않는_유저_조회시_예외발생() {
        when(userJpaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(999L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUser_반환값의_profileImageUrl은_조립된_public_URL이다() {
        UserEntity entity = createUserEntity(1L, "홍길동", "0001");
        entity.updateProfileImageUrl("profiles/1/abc.webp");
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(r2Uploader.toPublicUrl("profiles/1/abc.webp"))
                .thenReturn("https://cdn.example.com/profiles/1/abc.webp");

        User user = userService.getUser(1L);

        // @Cacheable 은 이 반환값을 그대로 캐시에 담는다.
        // chat/webrtc 가 Redis 를 직접 읽으므로 여기에 public URL 이 있어야 한다.
        assertThat(user.getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/1/abc.webp");
        verify(r2Uploader).toPublicUrl("profiles/1/abc.webp");
    }

    @Test
    void getUser_profileImageUrl이_null이면_R2Uploader를_호출하지_않는다() {
        UserEntity entity = createUserEntity(2L, "임꺽정", "0002");
        when(userJpaRepository.findById(2L)).thenReturn(Optional.of(entity));

        User user = userService.getUser(2L);

        assertThat(user.getProfileImageUrl()).isNull();
        verifyNoInteractions(r2Uploader);
    }
}
