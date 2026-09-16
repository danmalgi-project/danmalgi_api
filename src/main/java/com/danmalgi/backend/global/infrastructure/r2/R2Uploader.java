package com.danmalgi.backend.global.infrastructure.r2;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class R2Uploader {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp"
    );

    private static final Duration PRESIGN_DURATION = Duration.ofSeconds(3600);

    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client r2Client;
    private final S3Presigner r2Presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public R2Uploader(
            S3Client r2Client,
            S3Presigner r2Presigner,
            @Value("${r2.bucket}") String bucket,
            @Value("${r2.public-base-url}") String publicBaseUrl) {
        // 미설정이면 placeholder 해석 실패로 기동이 막히지만, 빈 값으로 주입되면 그 방어가 통하지 않는다.
        // 그대로 두면 조립 결과가 상대 경로가 되어 조용히 깨지므로 여기서 막는다.
        String normalizedBaseUrl = publicBaseUrl == null ? null : publicBaseUrl.replaceAll("/+$", "");
        Assert.hasText(normalizedBaseUrl, "r2.public-base-url must not be blank");

        this.r2Client = r2Client;
        this.r2Presigner = r2Presigner;
        this.bucket = bucket;
        this.publicBaseUrl = normalizedBaseUrl;
    }

    public String upload(Long userId, byte[] image, String extension) {
        return put("profiles/" + userId, image, extension);
    }

    public String uploadChannelImage(Long channelId, byte[] image, String extension) {
        return put("channels/" + channelId, image, extension);
    }

    private String put(String prefix, byte[] image, String extension) {
        String key = prefix + "/" + UUID.randomUUID() + "." + extension;

        r2Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(CONTENT_TYPES.get(extension.toLowerCase()))
                        .cacheControl(IMMUTABLE_CACHE_CONTROL)
                        .build(),
                RequestBody.fromBytes(image)
        );

        return key;
    }

    public String generatePresignedUrl(String key) {
        if (key.startsWith("http")) {
            return "";
        }

        return r2Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(PRESIGN_DURATION)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build())
                        .build()
        ).url().toString();
    }

    /**
     * object key 를 만료 없는 public URL 로 조립한다.
     *
     * <p>프로필 이미지 키는 {@code profiles/{userId}/{UUID}.webp} 로 immutable 하므로 서명이 필요 없다.
     * 만료가 없기 때문에 캐시(Redis {@code user:{id}})에 그대로 담을 수 있고, 그래야
     * chat_server / webrtc_server 가 캐시를 직접 읽어도 같은 값을 본다.
     *
     * <p>{@code http} 로 시작하는 값은 정의상 object key 가 아니므로(레거시 절대 URL)
     * 조립하지 않고 원본을 그대로 통과시킨다.
     */
    public String toPublicUrl(String key) {
        if (key == null || key.isBlank() || key.startsWith("http")) {
            return key;
        }

        return publicBaseUrl + "/" + key;
    }
}
