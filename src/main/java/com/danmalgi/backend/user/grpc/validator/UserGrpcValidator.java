package com.danmalgi.backend.user.grpc.validator;

import java.util.Set;

import com.danmalgi.backend.external.user.v1.UserProto;
import org.springframework.stereotype.Component;

@Component
public class UserGrpcValidator {

    private static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    public void validateVerifyNameAndTagRequest(UserProto.VerifyNameAndTagRequest request) {
        if (request.getName().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }

        if (request.getTag().isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
    }

    public void validateUploadProfileRequest(UserProto.UploadProfileRequest request) {
        if (request.getImage().isEmpty()) {
            throw new IllegalArgumentException("image must not be empty");
        }

        if (request.getImage().size() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("image size must not exceed 5MB");
        }

        if (request.getExtension().isBlank()) {
            throw new IllegalArgumentException("extension must not be blank");
        }

        if (!ALLOWED_EXTENSIONS.contains(request.getExtension().toLowerCase())) {
            throw new IllegalArgumentException("extension must be one of: jpg, jpeg, png, gif, webp");
        }
    }
}
