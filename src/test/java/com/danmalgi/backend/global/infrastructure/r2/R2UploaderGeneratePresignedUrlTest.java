package com.danmalgi.backend.global.infrastructure.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class R2UploaderGeneratePresignedUrlTest {

    @Mock
    private S3Client r2Client;

    @Mock
    private S3Presigner r2Presigner;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private R2Uploader r2Uploader;

    private static final String BUCKET = "danmalgi";

    @BeforeEach
    void setUp() {
        r2Uploader = new R2Uploader(r2Client, r2Presigner, BUCKET, "https://cdn.example.com");
    }

    @Test
    void generatePresignedUrl_key로_presigned_URL을_생성한다() throws MalformedURLException {
        // given
        String key = "profiles/1/uuid.png";
        String expectedUrl = "https://test.r2.cloudflarestorage.com/danmalgi/profiles/1/uuid.png?X-Amz-Signature=abc";

        when(r2Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url()).thenReturn(new URL(expectedUrl));

        // when
        String url = r2Uploader.generatePresignedUrl(key);

        // then
        assertThat(url).isEqualTo(expectedUrl);
        verify(r2Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void generatePresignedUrl_http로_시작하는_레거시_URL이면_빈문자열을_반환한다() {
        // given
        String legacyUrl = "https://test.r2.cloudflarestorage.com/danmalgi/profiles/1/uuid.png?X-Amz-Signature=expired";

        // when
        String result = r2Uploader.generatePresignedUrl(legacyUrl);

        // then
        assertThat(result).isEmpty();
    }
}
