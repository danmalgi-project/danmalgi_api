package com.danmalgi.backend.auth.domain.exception;

import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException.Reason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationCodeExchangeExceptionToDescriptionTest {

    @Test
    void toDescription_로그용_메시지를_클라이언트에_내보내지_않는다() {
        AuthorizationCodeExchangeException e =
                new AuthorizationCodeExchangeException(Reason.FAILED, "Apple token endpoint error: invalid_client");

        assertThat(e.toDescription()).isEqualTo("Authorization failed");
    }

    @Test
    void toDescription_REJECTED는_code_문제임을_알린다() {
        assertThat(new AuthorizationCodeExchangeException(Reason.REJECTED, "invalid_grant").toDescription())
                .isEqualTo("Invalid authorization code");
    }
}
