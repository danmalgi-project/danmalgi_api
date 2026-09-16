package com.danmalgi.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import com.danmalgi.backend.global.infrastructure.image.ImageProcessor;
import com.danmalgi.backend.global.infrastructure.image.exception.InvalidImageException;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.user.domain.exception.UserNotFoundException;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.fixture.UserFixture;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceUploadProfileTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private R2Uploader r2Uploader;

    @Mock
    private ImageProcessor imageProcessor;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userJpaRepository, redisTemplate, r2Uploader, imageProcessor);
    }

    @Test
    void uploadProfile_성공시_public_URL이_적용된_User를_반환한다() {
        // given
        Long userId = 1L;
        byte[] image = "test-image".getBytes();
        byte[] webpImage = "webp-bytes".getBytes();
        String rawKey = "profiles/1/uuid";
        String publicUrl = "https://cdn.example.com/profiles/1/uuid";

        UserEntity userEntity = UserFixture.createUserEntity(userId, "testUser", "tag1");
        when(userJpaRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(imageProcessor.toWebp(image)).thenReturn(webpImage);
        when(r2Uploader.upload(userId, webpImage, "webp")).thenReturn(rawKey);
        when(userJpaRepository.save(any(UserEntity.class))).thenReturn(userEntity);
        when(r2Uploader.toPublicUrl(rawKey)).thenReturn(publicUrl);

        // when
        User result = userService.uploadProfile(userId, image, "jpg");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getProfileImageUrl()).isEqualTo(publicUrl);
        verify(userJpaRepository).save(userEntity);
        verify(redisTemplate).delete(eq("user:" + userId));
        verify(r2Uploader).toPublicUrl(rawKey);
    }

    @Test
    void uploadProfile_존재하지않는유저면_UserNotFoundException이_발생한다() {
        // given
        Long userId = 999L;
        byte[] image = "test-image".getBytes();
        when(userJpaRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.uploadProfile(userId, image, "jpg"))
                .isInstanceOf(UserNotFoundException.class);

        verify(r2Uploader, never()).upload(anyLong(), any(), anyString());
        verify(userJpaRepository, never()).save(any());
    }

    @Test
    void uploadProfile_이미지는_webp로_변환되어_R2에_업로드된다() {
        Long userId = 1L;
        byte[] originalImage = "png-bytes".getBytes();
        byte[] webpImage = "webp-bytes".getBytes();
        String expectedUrl = "https://r2.example.com/danmalgi/profiles/1/abc.webp";

        UserEntity userEntity = UserFixture.createUserEntity(userId, "testUser", "tag1");
        when(userJpaRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(imageProcessor.toWebp(originalImage)).thenReturn(webpImage);
        when(r2Uploader.upload(userId, webpImage, "webp")).thenReturn(expectedUrl);
        when(userJpaRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        userService.uploadProfile(userId, originalImage, "png");

        verify(imageProcessor).toWebp(originalImage);
        verify(r2Uploader).upload(userId, webpImage, "webp");
    }

    @Test
    void uploadProfile_ImageProcessor가_InvalidImageException을_던지면_R2에_업로드하지_않는다() {
        Long userId = 1L;
        byte[] originalImage = "bad".getBytes();

        UserEntity userEntity = UserFixture.createUserEntity(userId, "testUser", "tag1");
        when(userJpaRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(imageProcessor.toWebp(originalImage))
                .thenThrow(new InvalidImageException("invalid image"));

        assertThatThrownBy(() -> userService.uploadProfile(userId, originalImage, "png"))
                .isInstanceOf(InvalidImageException.class);

        verify(r2Uploader, never()).upload(anyLong(), any(), anyString());
        verify(userJpaRepository, never()).save(any());
    }
}
