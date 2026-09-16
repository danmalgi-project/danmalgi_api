package com.danmalgi.backend.global.infrastructure.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class R2UploaderUploadChannelImageTest {

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
    void uploadChannelImage_성공시_channels_접두사_key를_반환한다() {
        Long channelId = 10L;
        byte[] image = "test-image".getBytes();

        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = r2Uploader.uploadChannelImage(channelId, image, "png");

        assertThat(key).startsWith("channels/10/");
        assertThat(key).endsWith(".png");
        verify(r2Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadChannelImage_extension에_맞는_contentType이_설정된다() {
        Long channelId = 10L;
        byte[] image = "test-image".getBytes();

        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

        r2Uploader.uploadChannelImage(channelId, image, "jpeg");

        verify(r2Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void uploadChannelImage_객체에_immutable_Cache_Control이_설정된다() {
        Long channelId = 10L;
        byte[] image = "test-image".getBytes();

        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

        r2Uploader.uploadChannelImage(channelId, image, "png");

        verify(r2Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().cacheControl())
                .isEqualTo("public, max-age=31536000, immutable");
    }
}
