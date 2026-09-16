package com.danmalgi.backend.dm.grpc.validator;

import java.util.List;
import java.util.Set;

import com.danmalgi.backend.external.dm.v1.DirectMessageProto;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Component;

@Component
public class DirectMessageGrpcValidator {

    private static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    public void validateUploadChannelImageRequest(DirectMessageProto.UploadChannelImageRequest request) {
        validateDmId(request.getDmId());
        validateRequiredImage(request.getImage(), request.getExtension());
    }

    public void validateUpdateDirectMessageChannelRequest(DirectMessageProto.UpdateDirectMessageChannelRequest request) {
        validateDmId(request.getDmId());

        boolean hasChannelName = !request.getChannelName().isBlank();
        boolean hasImage = !request.getImage().isEmpty();
        boolean hasExtension = !request.getExtension().isBlank();

        if (!hasChannelName && !hasImage) {
            throw new IllegalArgumentException("channel_name or image must be provided");
        }

        if (hasImage) {
            validateImage(request.getImage(), request.getExtension());
            return;
        }

        if (hasExtension) {
            throw new IllegalArgumentException("image must be provided when extension is set");
        }
    }

    public void validateLeaveDirectMessageChannelRequest(DirectMessageProto.LeaveDirectMessageChannelRequest request) {
        validateDmId(request.getDmId());
    }

    public void validateCreateDirectMessageChannelRequest(
            DirectMessageProto.CreateDirectMessageChannelRequest request,
            Long userId
    ) {
        List<Long> friendIds = request.getFriendIdsList();

        if (friendIds.isEmpty()) {
            throw new IllegalArgumentException("friend_ids must not be empty");
        }

        if (friendIds.contains(userId)) {
            throw new IllegalArgumentException("friend_ids must not contain the requester: " + userId);
        }

        if (Set.copyOf(friendIds).size() != friendIds.size()) {
            throw new IllegalArgumentException("friend_ids must not contain duplicated ids");
        }
    }

    private void validateDmId(long dmId) {
        if (dmId <= 0L) {
            throw new IllegalArgumentException("dm_id must be positive");
        }
    }

    private void validateRequiredImage(ByteString image, String extension) {
        if (image.isEmpty()) {
            throw new IllegalArgumentException("image must not be empty");
        }

        validateImage(image, extension);
    }

    private void validateImage(ByteString image, String extension) {
        if (image.size() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("image size must not exceed 5MB");
        }

        if (extension.isBlank()) {
            throw new IllegalArgumentException("extension must not be blank");
        }

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("extension must be one of: jpg, jpeg, png, gif, webp");
        }
    }
}
