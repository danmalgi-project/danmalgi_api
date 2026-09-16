package com.danmalgi.backend.user.domain.exception;

import com.danmalgi.backend.global.exceptionhandler.GrpcException;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserValidateException extends RuntimeException implements GrpcException {
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
