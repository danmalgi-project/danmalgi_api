package com.danmalgi.backend.global.exceptionhandler;

import io.grpc.Status;

public interface GrpcException {
    Status getStatus();
    String toDescription();
}
