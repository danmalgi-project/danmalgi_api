package com.danmalgi.backend.global.infrastructure.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.springframework.stereotype.Component;

import com.danmalgi.backend.global.infrastructure.image.exception.InvalidImageException;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;

@Component
public class ImageProcessor {

    private static final int MAX_DIMENSION = 512;
    private static final int WEBP_QUALITY = 80;

    public byte[] toWebp(byte[] image) {
        if (image == null || image.length == 0) {
            throw new InvalidImageException("invalid image");
        }

        ImmutableImage decoded;
        try {
            decoded = ImmutableImage.loader().fromStream(new ByteArrayInputStream(image));
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException("invalid image");
        }

        ImmutableImage resized = resizeIfNeeded(decoded);

        try {
            return resized.bytes(WebpWriter.DEFAULT.withQ(WEBP_QUALITY));
        } catch (IOException e) {
            throw new InvalidImageException("failed to encode webp");
        }
    }

    private ImmutableImage resizeIfNeeded(ImmutableImage image) {
        int longEdge = Math.max(image.width, image.height);
        if (longEdge <= MAX_DIMENSION) {
            return image;
        }
        double scale = (double) MAX_DIMENSION / longEdge;
        int newW = (int) Math.round(image.width * scale);
        int newH = (int) Math.round(image.height * scale);
        return image.scaleTo(newW, newH);
    }
}
