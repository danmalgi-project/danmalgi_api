package com.danmalgi.backend.global.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.danmalgi.backend.global.infrastructure.image.exception.InvalidImageException;
import com.sksamuel.scrimage.ImmutableImage;

class ImageProcessorToWebpTest {

    private final ImageProcessor imageProcessor = new ImageProcessor();

    @Test
    void toWebp_PNG_입력이면_webp_바이트로_변환된다() throws IOException {
        byte[] png = createImage(100, 100, "png");
        byte[] webp = imageProcessor.toWebp(png);
        assertThat(webp).isNotEmpty();
        assertThat(new String(webp, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(webp, 8, 4)).isEqualTo("WEBP");
    }

    @Test
    void toWebp_JPEG_입력이면_webp_바이트로_변환된다() throws IOException {
        byte[] jpeg = createImage(100, 100, "jpg");
        byte[] webp = imageProcessor.toWebp(jpeg);
        assertThat(new String(webp, 0, 4)).isEqualTo("RIFF");
    }

    @Test
    void toWebp_큰_이미지는_긴변이_512로_축소된다() throws IOException {
        byte[] png = createImage(1024, 768, "png");
        byte[] webp = imageProcessor.toWebp(png);
        ImmutableImage decoded = ImmutableImage.loader().fromStream(new ByteArrayInputStream(webp));
        assertThat(decoded.width).isEqualTo(512);
        assertThat(decoded.height).isEqualTo(384);
    }

    @Test
    void toWebp_작은_이미지는_크기_변경없음() throws IOException {
        byte[] png = createImage(200, 150, "png");
        byte[] webp = imageProcessor.toWebp(png);
        ImmutableImage decoded = ImmutableImage.loader().fromStream(new ByteArrayInputStream(webp));
        assertThat(decoded.width).isEqualTo(200);
        assertThat(decoded.height).isEqualTo(150);
    }

    @Test
    void toWebp_디코딩_실패하면_InvalidImageException() {
        byte[] garbage = "not-an-image".getBytes();
        assertThatThrownBy(() -> imageProcessor.toWebp(garbage))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void toWebp_빈_바이트이면_InvalidImageException() {
        assertThatThrownBy(() -> imageProcessor.toWebp(new byte[0]))
                .isInstanceOf(InvalidImageException.class);
    }

    private byte[] createImage(int w, int h, String format) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }
}
