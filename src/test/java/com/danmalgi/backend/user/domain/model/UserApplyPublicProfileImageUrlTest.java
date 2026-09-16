package com.danmalgi.backend.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;

@ExtendWith(MockitoExtension.class)
class UserApplyPublicProfileImageUrlTest {

    @Mock
    private R2Uploader r2Uploader;

    private User validUser() {
        return new User(1L, "u@test.com", "tester", "0001", "device-1");
    }

    @Test
    void profileImageUrl이_object_key이면_toPublicUrl_결과로_치환된다() {
        User user = validUser();
        user.setProfileImageUrl("profiles/1/abc.webp");
        when(r2Uploader.toPublicUrl("profiles/1/abc.webp"))
                .thenReturn("https://cdn.example.com/profiles/1/abc.webp");

        user.applyPublicProfileImageUrl(r2Uploader);

        assertThat(user.getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/1/abc.webp");
        verify(r2Uploader).toPublicUrl("profiles/1/abc.webp");
    }

    @Test
    void profileImageUrl이_null이면_R2Uploader_미호출하고_null_유지() {
        User user = validUser();
        user.setProfileImageUrl(null);

        user.applyPublicProfileImageUrl(r2Uploader);

        assertThat(user.getProfileImageUrl()).isNull();
        verifyNoInteractions(r2Uploader);
    }

    @Test
    void profileImageUrl이_빈문자열이면_R2Uploader_미호출하고_빈문자열_유지() {
        User user = validUser();
        user.setProfileImageUrl("");

        user.applyPublicProfileImageUrl(r2Uploader);

        assertThat(user.getProfileImageUrl()).isEmpty();
        verify(r2Uploader, never()).toPublicUrl(anyString());
    }

    @Test
    void profileImageUrl이_blank공백이면_R2Uploader_미호출() {
        User user = validUser();
        user.setProfileImageUrl("   ");

        user.applyPublicProfileImageUrl(r2Uploader);

        assertThat(user.getProfileImageUrl()).isEqualTo("   ");
        verifyNoInteractions(r2Uploader);
    }

    @Test
    void profileImageUrl이_http로_시작하면_원본이_그대로_유지된다() {
        // Q1 확정: 과거에는 generatePresignedUrl 이 "" 를 반환해 이미지가 죽었다.
        // 이제는 toPublicUrl 이 원본을 통과시키므로 레거시 절대 URL 이 살아난다.
        User user = validUser();
        user.setProfileImageUrl("http://legacy.example.com/img.png");
        when(r2Uploader.toPublicUrl("http://legacy.example.com/img.png"))
                .thenReturn("http://legacy.example.com/img.png");

        user.applyPublicProfileImageUrl(r2Uploader);

        assertThat(user.getProfileImageUrl()).isEqualTo("http://legacy.example.com/img.png");
        verify(r2Uploader).toPublicUrl("http://legacy.example.com/img.png");
    }

    @Test
    void applyPublicProfileImageUrl은_presign을_호출하지_않는다() {
        User user = validUser();
        user.setProfileImageUrl("profiles/1/abc.webp");
        when(r2Uploader.toPublicUrl("profiles/1/abc.webp"))
                .thenReturn("https://cdn.example.com/profiles/1/abc.webp");

        user.applyPublicProfileImageUrl(r2Uploader);

        verify(r2Uploader, never()).generatePresignedUrl(anyString());
    }
}
