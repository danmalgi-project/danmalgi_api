package com.danmalgi.backend.store.dm_store.grpc.validator;

import com.danmalgi.backend.internal.dm_store.v1.DmStoreProto.GetChannelNamesRequest;
import org.springframework.stereotype.Component;

@Component
public class DmStoreGrpcValidator {

    public void validateGetChannelNamesRequest(GetChannelNamesRequest request) {
        if (request.getChannelId() <= 0) {
            throw new IllegalArgumentException("channelId must be positive");
        }
    }
}
