package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Apple 호스트로 나가는 요청만 로컬 가짜 서버로 돌린다. <b>local 프로필 전용</b>이다.
 *
 * <p>왜 {@link AppleIdTokenVerifier#JWK_SET_URI} 와
 * {@link AppleAuthorizationCodeExchangeAdapter#TOKEN_URI} 를 설정값으로 빼지 않았나:
 * JWKS URI 는 Apple idToken 신뢰의 기점이다. 설정 한 줄로 그 기점을 옮길 수 있게 되면
 * 설정 실수나 설정값 탈취만으로 아무 서명 토큰이나 통과시킬 수 있다. 그래서 상수는 코드에
 * 고정해 두고, 우회로는 {@code @Profile("local")} 에서만 만들어지는 빈 안에 가둔다.
 * 운영 프로필에는 이 클래스를 생성하는 경로 자체가 없다.
 *
 * <p>로컬 가짜 서버는 평문 HTTP 라 호스트만이 아니라 scheme 과 port 까지 바꾼다.
 */
final class AppleLocalHostRewriteRequestFactory implements ClientHttpRequestFactory {
    private static final String APPLE_HOST = "appleid.apple.com";

    private final ClientHttpRequestFactory delegate;
    private final URI baseUrl;

    AppleLocalHostRewriteRequestFactory(ClientHttpRequestFactory delegate, String baseUrl) {
        this.delegate = delegate;
        this.baseUrl = URI.create(baseUrl.trim());
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
        return delegate.createRequest(rewrite(uri), httpMethod);
    }

    /**
     * Apple 이 아닌 호스트는 건드리지 않는다. 이 빈이 실수로 다른 곳에 주입되더라도
     * Google JWKS 가 가짜 서버로 넘어가지 않게 하려는 것이다.
     */
    private URI rewrite(URI uri) {
        if (!APPLE_HOST.equalsIgnoreCase(uri.getHost())) {
            return uri;
        }
        try {
            return new URI(
                    baseUrl.getScheme(),
                    null,
                    baseUrl.getHost(),
                    baseUrl.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment());
        } catch (URISyntaxException e) {
            // base-url 이 잘못 설정된 것이다. 로컬 전용이므로 기동이 아니라 호출 시점에 드러나도 된다.
            throw new IllegalStateException("oauth.apple.local.base-url is invalid: " + baseUrl, e);
        }
    }
}
