package com.danmalgi.backend.global.infrastructure.r2;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class R2UploaderConstructorTest {

    @Mock
    private S3Client r2Client;

    @Mock
    private S3Presigner r2Presigner;

    private static final String BUCKET = "danmalgi";

    /**
     * r2.public-base-url 미설정은 Spring 의 placeholder 해석 실패로 기동이 막힌다.
     * 그러나 환경변수를 빈 값으로 주입하면 placeholder 는 해석되므로 그 방어가 통하지 않는다.
     * 이 경우 조립 결과가 상대 경로가 되어 조용히 깨지므로 생성자에서 막는다.
     */
    @Test
    void publicBaseUrl이_빈_문자열이면_생성에_실패한다() {
        assertThatThrownBy(() -> new R2Uploader(r2Client, r2Presigner, BUCKET, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicBaseUrl이_공백뿐이면_생성에_실패한다() {
        assertThatThrownBy(() -> new R2Uploader(r2Client, r2Presigner, BUCKET, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicBaseUrl이_null이면_생성에_실패한다() {
        assertThatThrownBy(() -> new R2Uploader(r2Client, r2Presigner, BUCKET, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicBaseUrl이_slash뿐이면_정규화_후_빈_값이_되므로_생성에_실패한다() {
        assertThatThrownBy(() -> new R2Uploader(r2Client, r2Presigner, BUCKET, "///"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicBaseUrl이_유효하면_생성에_성공한다() {
        assertThatCode(() -> new R2Uploader(r2Client, r2Presigner, BUCKET, "https://cdn.example.com"))
                .doesNotThrowAnyException();
    }
}
