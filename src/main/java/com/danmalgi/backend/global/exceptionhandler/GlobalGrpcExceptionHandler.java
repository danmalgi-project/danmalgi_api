package com.danmalgi.backend.global.exceptionhandler;

import java.util.NoSuchElementException;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.Status;
import org.springframework.stereotype.Component;

@Component
public class GlobalGrpcExceptionHandler implements GrpcExceptionHandler {
    @Override
    public @Nullable StatusException handleException(Throwable ex) {
        return switch (ex) {
            case StatusException statusException -> statusException;
            case GrpcException grpcException ->
                    grpcException.getStatus()
                            .withDescription(grpcException.toDescription())
                            .asException();
            case StatusRuntimeException statusRuntimeException ->
                    statusRuntimeException.getStatus().asException(statusRuntimeException.getTrailers());
            case IllegalArgumentException illegalArgumentException ->
                    Status.INVALID_ARGUMENT
                            .withDescription(messageOrDefault(illegalArgumentException, "Invalid request"))
                            .asException();
            case DataIntegrityViolationException dataIntegrityViolationException ->
                    Status.ALREADY_EXISTS
                            .withDescription(messageOrDefault(dataIntegrityViolationException, "Resource already exists"))
                            .asException();
            case AccessDeniedException accessDeniedException ->
                    Status.PERMISSION_DENIED
                            .withDescription(messageOrDefault(accessDeniedException, "Permission denied"))
                            .asException();
            case SecurityException securityException ->
                    Status.PERMISSION_DENIED
                            .withDescription(messageOrDefault(securityException, "Permission denied"))
                            .asException();
            case NoSuchElementException noSuchElementException ->
                    Status.NOT_FOUND
                            .withDescription(messageOrDefault(noSuchElementException, "Resource not found"))
                            .asException();
            case UnsupportedOperationException unsupportedOperationException ->
                    Status.UNIMPLEMENTED
                            .withDescription(messageOrDefault(unsupportedOperationException, "Operation not implemented"))
                            .asException();
            case IllegalStateException illegalStateException ->
                    Status.FAILED_PRECONDITION
                            .withDescription(messageOrDefault(illegalStateException, "Request cannot be processed in the current state"))
                            .asException();
            default ->
                    Status.INTERNAL
                            .withDescription(messageOrDefault(ex, "Internal server error"))
                            .asException();
        };
    }

    private String messageOrDefault(Throwable ex, String defaultMessage) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return defaultMessage;
        }
        return message;
    }
}
