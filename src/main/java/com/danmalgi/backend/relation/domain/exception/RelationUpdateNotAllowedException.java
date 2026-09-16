package com.danmalgi.backend.relation.domain.exception;

import com.danmalgi.backend.global.exceptionhandler.GrpcException;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RelationUpdateNotAllowedException extends RuntimeException implements GrpcException {
    private final String description;

    @Override
    public Status getStatus() {
        return Status.FAILED_PRECONDITION;
    }

    @Override
    public String toDescription() {
        return description;
    }
}
