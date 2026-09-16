package com.danmalgi.backend.global.infrastructure.image.exception;

import com.danmalgi.backend.global.exceptionhandler.GrpcException;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InvalidImageException extends RuntimeException implements GrpcException {
    private final String description;

    @Override
    public Status getStatus() {
        return Status.INVALID_ARGUMENT;
    }

    @Override
    public String toDescription() {
        return description;
    }
}
