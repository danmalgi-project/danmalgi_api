package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class AppleLocalHostRewriteRequestFactoryCreateRequestTest {
    private static final String BASE_URL = "http://127.0.0.1:9099";

    private final RecordingRequestFactory delegate = new RecordingRequestFactory();
    private final AppleLocalHostRewriteRequestFactory factory =
            new AppleLocalHostRewriteRequestFactory(delegate, BASE_URL);

    @Test
    void createRequest_Apple_JWKS_URI면_scheme_host_port가_모두_교체된다() throws Exception {
        factory.createRequest(URI.create(AppleIdTokenVerifier.JWK_SET_URI), HttpMethod.GET);

        assertThat(delegate.lastUri).hasToString("http://127.0.0.1:9099/auth/keys");
    }

    @Test
    void createRequest_Apple_토큰_URI면_로컬로_교체된다() throws Exception {
        factory.createRequest(URI.create(AppleAuthorizationCodeExchangeAdapter.TOKEN_URI), HttpMethod.POST);

        assertThat(delegate.lastUri).hasToString("http://127.0.0.1:9099/auth/token");
    }

    @Test
    void createRequest_Apple_이_아닌_호스트면_그대로_둔다() throws Exception {
        // 이 빈이 실수로 공유돼도 Google JWKS 가 가짜 서버로 가면 안 된다.
        URI googleJwks = URI.create("https://www.googleapis.com/oauth2/v3/certs");

        factory.createRequest(googleJwks, HttpMethod.GET);

        assertThat(delegate.lastUri).isEqualTo(googleJwks);
    }

    @Test
    void createRequest_호스트_대소문자가_달라도_교체된다() throws Exception {
        factory.createRequest(URI.create("https://APPLEID.APPLE.COM/auth/keys"), HttpMethod.GET);

        assertThat(delegate.lastUri).hasToString("http://127.0.0.1:9099/auth/keys");
    }

    @Test
    void createRequest_path와_query를_보존한다() throws Exception {
        factory.createRequest(URI.create("https://appleid.apple.com/auth/keys?v=2"), HttpMethod.GET);

        assertThat(delegate.lastUri).hasToString("http://127.0.0.1:9099/auth/keys?v=2");
    }

    @Test
    void createRequest_HTTP_메서드는_그대로_넘긴다() throws Exception {
        factory.createRequest(URI.create(AppleAuthorizationCodeExchangeAdapter.TOKEN_URI), HttpMethod.POST);

        assertThat(delegate.lastMethod).isEqualTo(HttpMethod.POST);
    }

    /** 위임 대상에 무엇이 넘어갔는지만 본다. 실제 연결은 하지 않는다. */
    private static final class RecordingRequestFactory implements ClientHttpRequestFactory {
        private URI lastUri;
        private HttpMethod lastMethod;

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            this.lastUri = uri;
            this.lastMethod = httpMethod;
            return null;
        }
    }
}
