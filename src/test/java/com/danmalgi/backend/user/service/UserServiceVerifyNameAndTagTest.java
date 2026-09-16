package com.danmalgi.backend.user.service;

import static com.danmalgi.backend.user.fixture.UserFixture.createUserEntity;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import com.danmalgi.backend.user.domain.exception.DuplicatedUserException;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceVerifyNameAndTagTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void verifyNameAndTag_사용되지_않은_조합_검증_성공() {
        when(userJpaRepository.findByNameAndTag("홍길동", "0001")).thenReturn(Optional.empty());

        assertThatCode(() -> userService.verifyNameAndTag("홍길동", "0001"))
                .doesNotThrowAnyException();
    }

    @Test
    void verifyNameAndTag_이미_사용중인_name_tag_예외발생() {
        when(userJpaRepository.findByNameAndTag("홍길동", "0001"))
                .thenReturn(Optional.of(createUserEntity(1L, "홍길동", "0001")));

        assertThatThrownBy(() -> userService.verifyNameAndTag("홍길동", "0001"))
                .isInstanceOf(DuplicatedUserException.class);
    }

    @Test
    void verifyNameAndTag_같은_name_다른_tag_검증_성공() {
        when(userJpaRepository.findByNameAndTag("홍길동", "0002")).thenReturn(Optional.empty());

        assertThatCode(() -> userService.verifyNameAndTag("홍길동", "0002"))
                .doesNotThrowAnyException();
    }

    @Test
    void verifyNameAndTag_다른_name_같은_tag_검증_성공() {
        when(userJpaRepository.findByNameAndTag("김철수", "0001")).thenReturn(Optional.empty());

        assertThatCode(() -> userService.verifyNameAndTag("김철수", "0001"))
                .doesNotThrowAnyException();
    }
}
