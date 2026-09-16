package com.danmalgi.backend.dm.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;

// 도메인 메서드는 in-place mutation (void 반환). plan 코드 스케치 따름.
@ExtendWith(MockitoExtension.class)
class DirectMessageApplyPresignedChannelImageUrlTest {

    @Mock
    private R2Uploader r2Uploader;

    private DirectMessage baseDirectMessage(String channelImageUrl) {
        return DirectMessage.builder()
                .id(10L)
                .channelName("group")
                .isGroup(true)
                .channelImageUrl(channelImageUrl)
                .users(List.of())
                .build();
    }

    @Test
    void channelImageUrl이_path이면_R2Uploader_호출_후_presigned_URL로_치환된다() {
        DirectMessage dm = baseDirectMessage("channels/10/x.webp");
        when(r2Uploader.generatePresignedUrl("channels/10/x.webp"))
                .thenReturn("https://r2.example.com/channels/10/x.webp?sig=yyy");

        dm.applyPresignedChannelImageUrl(r2Uploader);

        assertThat(dm.getChannelImageUrl())
                .isEqualTo("https://r2.example.com/channels/10/x.webp?sig=yyy");
        verify(r2Uploader).generatePresignedUrl("channels/10/x.webp");
    }

    @Test
    void channelImageUrl이_null이면_R2Uploader_미호출하고_null_유지() {
        DirectMessage dm = baseDirectMessage(null);

        dm.applyPresignedChannelImageUrl(r2Uploader);

        assertThat(dm.getChannelImageUrl()).isNull();
        verifyNoInteractions(r2Uploader);
    }

    @Test
    void channelImageUrl이_빈문자열이면_R2Uploader_미호출하고_빈문자열_유지() {
        DirectMessage dm = baseDirectMessage("");

        dm.applyPresignedChannelImageUrl(r2Uploader);

        assertThat(dm.getChannelImageUrl()).isEmpty();
        verify(r2Uploader, never()).generatePresignedUrl(anyString());
    }

    @Test
    void channelImageUrl이_blank공백이면_R2Uploader_미호출() {
        DirectMessage dm = baseDirectMessage("   ");

        dm.applyPresignedChannelImageUrl(r2Uploader);

        assertThat(dm.getChannelImageUrl()).isEqualTo("   ");
        verifyNoInteractions(r2Uploader);
    }

    @Test
    void channelImageUrl이_http로_시작하면_R2Uploader_위임결과_빈문자열이_세팅된다() {
        DirectMessage dm = baseDirectMessage("http://legacy/img");
        when(r2Uploader.generatePresignedUrl("http://legacy/img"))
                .thenReturn("");

        dm.applyPresignedChannelImageUrl(r2Uploader);

        assertThat(dm.getChannelImageUrl()).isEmpty();
        verify(r2Uploader).generatePresignedUrl("http://legacy/img");
    }
}
