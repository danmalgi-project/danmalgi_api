package com.danmalgi.backend.user.service;

import static com.danmalgi.backend.user.fixture.UserFixture.createUserEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceSearchUserTest {

    private static final Long REQUESTER_ID = 1L;

    @InjectMocks
    private UserService userService;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private R2Uploader r2Uploader;

    @Test
    void searchUser_존재하는_name_tag면_한명_반환() {
        UserEntity entity = createUserEntity(2L, "홍길동", "0001");
        when(userJpaRepository.findByNameAndTag("홍길동", "0001")).thenReturn(Optional.of(entity));

        List<User> users = userService.searchUser(REQUESTER_ID, "홍길동", "0001");

        assertThat(users).singleElement().satisfies(user -> {
            assertThat(user.getId()).isEqualTo(2L);
            assertThat(user.getName()).isEqualTo("홍길동");
            assertThat(user.getTag()).isEqualTo("0001");
        });
    }

    @Test
    void searchUser_없는_name_tag면_빈리스트_반환() {
        when(userJpaRepository.findByNameAndTag("없음", "9999")).thenReturn(Optional.empty());

        List<User> users = userService.searchUser(REQUESTER_ID, "없음", "9999");

        assertThat(users).isEmpty();
    }

    @Test
    void searchUser_자기자신이면_빈리스트_반환() {
        UserEntity me = createUserEntity(REQUESTER_ID, "나", "0001");
        when(userJpaRepository.findByNameAndTag("나", "0001")).thenReturn(Optional.of(me));

        List<User> users = userService.searchUser(REQUESTER_ID, "나", "0001");

        assertThat(users).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void searchUser_ACTIVE가_아니면_빈리스트_반환(UserStatus status) {
        UserEntity entity = createUserEntity(2L, "홍길동", "0001");
        entity.updateStatus(status.getNumber());
        when(userJpaRepository.findByNameAndTag("홍길동", "0001")).thenReturn(Optional.of(entity));

        List<User> users = userService.searchUser(REQUESTER_ID, "홍길동", "0001");

        assertThat(users).isEmpty();
    }

    @Test
    void searchUser_앞뒤_공백은_trim해서_조회() {
        UserEntity entity = createUserEntity(2L, "홍길동", "0001");
        when(userJpaRepository.findByNameAndTag("홍길동", "0001")).thenReturn(Optional.of(entity));

        List<User> users = userService.searchUser(REQUESTER_ID, "  홍길동 ", " 0001  ");

        assertThat(users).hasSize(1);
        verify(userJpaRepository).findByNameAndTag("홍길동", "0001");
    }

    @Test
    void searchUser_profileImageUrl_키가_있으면_public_URL로_변환() {
        UserEntity entity = createUserEntity(2L, "홍길동", "0001");
        entity.updateProfileImageUrl("profiles/2/abc.webp");
        when(userJpaRepository.findByNameAndTag("홍길동", "0001")).thenReturn(Optional.of(entity));
        when(r2Uploader.toPublicUrl("profiles/2/abc.webp"))
                .thenReturn("https://cdn.example.com/profiles/2/abc.webp");

        List<User> users = userService.searchUser(REQUESTER_ID, "홍길동", "0001");

        assertThat(users).singleElement()
                .extracting(User::getProfileImageUrl)
                .isEqualTo("https://cdn.example.com/profiles/2/abc.webp");
    }

    @Test
    void searchUser_profileImageUrl이_없으면_R2Uploader를_호출하지_않는다() {
        UserEntity entity = createUserEntity(2L, "홍길동", "0001");
        when(userJpaRepository.findByNameAndTag("홍길동", "0001")).thenReturn(Optional.of(entity));

        List<User> users = userService.searchUser(REQUESTER_ID, "홍길동", "0001");

        assertThat(users).singleElement()
                .extracting(User::getProfileImageUrl)
                .isNull();
        verifyNoInteractions(r2Uploader);
    }
}
