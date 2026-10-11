package com.danmalgi.backend.auth.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OauthProviderUnavailableExceptionToDescriptionTest {

    @Test
    void toDescription_로그용_메시지를_클라이언트에_내보내지_않는다() {
        OauthProviderUnavailableException e =
                new OauthProviderUnavailableException("Couldn't retrieve remote JWK set: connect timed out", null);

        assertThat(e.toDescription()).isEqualTo("OAuth provider is unavailable");
    }
}
