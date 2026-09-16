package com.danmalgi.backend.global.infrastructure.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class R2UploaderToPublicUrlTest {

    @Mock
    private S3Client r2Client;

    @Mock
    private S3Presigner r2Presigner;

    private static final String BUCKET = "danmalgi";
    private static final String PUBLIC_BASE_URL = "https://cdn.example.com";

    private R2Uploader uploaderWithBaseUrl(String publicBaseUrl) {
        return new R2Uploader(r2Client, r2Presigner, BUCKET, publicBaseUrl);
    }

    @Test
    void toPublicUrl_object_key를_base_url과_조립한다() {
        R2Uploader r2Uploader = uploaderWithBaseUrl(PUBLIC_BASE_URL);

        String result = r2Uploader.toPublicUrl("profiles/1/abc.webp");

        assertThat(result).isEqualTo("https://cdn.example.com/profiles/1/abc.webp");
    }

    @Test
    void toPublicUrl_base_url의_trailing_slash를_정규화한다() {
        R2Uploader r2Uploader = uploaderWithBaseUrl("https://cdn.example.com/");

        String result = r2Uploader.toPublicUrl("profiles/1/abc.webp");

        assertThat(result).isEqualTo("https://cdn.example.com/profiles/1/abc.webp");
    }

    @Test
    void toPublicUrl_base_url의_trailing_slash가_여러개여도_정규화한다() {
        R2Uploader r2Uploader = uploaderWithBaseUrl("https://cdn.example.com///");

        String result = r2Uploader.toPublicUrl("profiles/1/abc.webp");

        assertThat(result).isEqualTo("https://cdn.example.com/profiles/1/abc.webp");
    }

    @Test
    void toPublicUrl_http로_시작하는_레거시_값은_원본_그대로_반환한다() {
        // Q1 확정: 조립하지 않고 원본을 통과시킨다. 과거처럼 "" 로 죽이지 않는다.
        R2Uploader r2Uploader = uploaderWithBaseUrl(PUBLIC_BASE_URL);

        String result = r2Uploader.toPublicUrl("http://legacy.example.com/img.png");

        assertThat(result).isEqualTo("http://legacy.example.com/img.png");
    }

    @Test
    void toPublicUrl_https로_시작하는_값도_원본_그대로_반환한다() {
        R2Uploader r2Uploader = uploaderWithBaseUrl(PUBLIC_BASE_URL);

        String result = r2Uploader.toPublicUrl("https://cdn.example.com/profiles/1/abc.webp");

        assertThat(result).isEqualTo("https://cdn.example.com/profiles/1/abc.webp");
    }

    @Test
    void toPublicUrl_null이면_null을_반환한다() {
        R2Uploader r2Uploader = uploaderWithBaseUrl(PUBLIC_BASE_URL);

        assertThat(r2Uploader.toPublicUrl(null)).isNull();
    }

    @Test
    void toPublicUrl_빈문자열이면_원본_그대로_반환한다() {
        R2Uploader r2Uploader = uploaderWithBaseUrl(PUBLIC_BASE_URL);

        assertThat(r2Uploader.toPublicUrl("")).isEmpty();
    }

    @Test
    void toPublicUrl_공백문자열이면_원본_그대로_반환한다() {
        R2Uploader r2Uploader = uploaderWithBaseUrl(PUBLIC_BASE_URL);

        assertThat(r2Uploader.toPublicUrl("   ")).isEqualTo("   ");
    }

    @Test
    void toPublicUrl_presigner를_호출하지_않는다() {
        R2Uploader r2Uploader = uploaderWithBaseUrl(PUBLIC_BASE_URL);

        r2Uploader.toPublicUrl("profiles/1/abc.webp");

        verifyNoInteractions(r2Presigner);
        verifyNoInteractions(r2Client);
    }
}
