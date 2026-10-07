package com.danmalgi.backend.auth.domain.exception;

import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException.Reason;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationCodeExchangeExceptionGetStatusTest {

    @Test
    void getStatus_REJECTED는_재로그인을_뜻하는_UNAUTHENTICATED() {
        assertThat(new AuthorizationCodeExchangeException(Reason.REJECTED, "invalid_grant").getStatus())
                .isEqualTo(Status.UNAUTHENTICATED);
    }

    @Test
    void getStatus_UNAVAILABLE은_재시도를_뜻하는_UNAVAILABLE() {
        assertThat(new AuthorizationCodeExchangeException(Reason.UNAVAILABLE, "timeout").getStatus())
                .isEqualTo(Status.UNAVAILABLE);
    }

    @Test
    void getStatus_FAILED는_서버_설정_오류인_INTERNAL() {
        assertThat(new AuthorizationCodeExchangeException(Reason.FAILED, "invalid_client").getStatus())
                .isEqualTo(Status.INTERNAL);
    }
}
