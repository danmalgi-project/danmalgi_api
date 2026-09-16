package com.danmalgi.backend.global.infrastructure.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class R2UploaderTest {

    @Mock
    private S3Client r2Client;

    @Mock
    private S3Presigner r2Presigner;

    private R2Uploader r2Uploader;

    private static final String BUCKET = "danmalgi";

    @BeforeEach
    void setUp() {
        r2Uploader = new R2Uploader(r2Client, r2Presigner, BUCKET, "https://cdn.example.com");
    }

    @Test
    void upload_성공시_key를_반환한다() {
        // given
        Long userId = 1L;
        byte[] image = "test-image".getBytes();

        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        String key = r2Uploader.upload(userId, image, "png");

        // then
        assertThat(key).startsWith("profiles/1/");
        assertThat(key).endsWith(".png");
        verify(r2Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @ParameterizedTest
    @CsvSource({
            "jpg,  image/jpeg",
            "jpeg, image/jpeg",
            "png,  image/png",
            "gif,  image/gif",
            "webp, image/webp"
    })
    void upload_extension에_맞는_contentType이_설정된다(String extension, String expectedContentType) {
        // given
        Long userId = 1L;
        byte[] image = "test-image".getBytes();

        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

        // when
        r2Uploader.upload(userId, image, extension.trim());

        // then
        verify(r2Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().contentType()).isEqualTo(expectedContentType.trim());
    }

    @Test
    void upload_S3Client_예외발생시_예외가_전파된다() {
        // given
        Long userId = 1L;
        byte[] image = "test-image".getBytes();
        when(r2Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenThrow(S3Exception.builder().message("upload failed").build());

        // when & then
        assertThatThrownBy(() -> r2Uploader.upload(userId, image, "jpg"))
                .isInstanceOf(S3Exception.class);
    }

    @Test
    void upload_객체에_immutable_Cache_Control이_설정된다() {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

        r2Uploader.upload(1L, "test-image".getBytes(), "webp");

        verify(r2Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().cacheControl())
                .isEqualTo("public, max-age=31536000, immutable");
    }
}
