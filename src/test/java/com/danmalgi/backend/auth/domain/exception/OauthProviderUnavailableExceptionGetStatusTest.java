package com.danmalgi.backend.auth.domain.exception;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OauthProviderUnavailableExceptionGetStatusTest {

    @Test
    void getStatus_제공자_장애는_재시도를_뜻하는_UNAVAILABLE() {
        assertThat(new OauthProviderUnavailableException("JWKS unavailable", null).getStatus())
                .isEqualTo(Status.UNAVAILABLE);
    }
}
