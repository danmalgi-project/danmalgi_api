package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException;
import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException.Reason;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthAuthorizationCodeExchangePort;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.nimbusds.jose.util.JSONObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class AppleAuthorizationCodeExchangeAdapter implements OAuthAuthorizationCodeExchangePort {
    static final String TOKEN_URI = "https://appleid.apple.com/auth/token";

    private final RestOperations restOperations;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final String webClientId;
    private final String redirectUri;

    @Autowired
    public AppleAuthorizationCodeExchangeAdapter(
            AppleClientSecretGenerator clientSecretGenerator,
            @Value("${oauth.apple.web-client-id}") String webClientId,
            @Value("${oauth.apple.redirect-uri}") String redirectUri
    ) {
        this(createRestOperations(), clientSecretGenerator, webClientId, redirectUri);
    }

    AppleAuthorizationCodeExchangeAdapter(
            RestOperations restOperations,
            AppleClientSecretGenerator clientSecretGenerator,
            String webClientId,
            String redirectUri
    ) {
        this.restOperations = restOperations;
        this.clientSecretGenerator = clientSecretGenerator;
        this.webClientId = webClientId.trim();
        this.redirectUri = redirectUri.trim();
    }

    private static RestOperations createRestOperations() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(requestFactory);
    }

    @Override
    public OauthType supportedOauthType() {
        return OauthType.APPLE;
    }

    @Override
    public String exchange(String authorizationCode, String clientId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecretGenerator.generate(clientId));
        // Android 는 웹 플로우(Services ID)로 code 를 받는다. 웹 플로우 code 는 발급 때 쓴
        // redirect_uri 를 같이 보내야 교환된다. iOS 네이티브(Bundle ID)는 보내면 안 된다.
        if (webClientId.equals(clientId)) {
            form.add("redirect_uri", redirectUri);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = post(new HttpEntity<>(form, headers), clientId);
        return readRefreshToken(response.getBody(), clientId);
    }

    // 응답 본문과 code 에는 토큰이 들어 있으므로 로그에 남기지 않는다. error 코드만 남긴다.
    private ResponseEntity<String> post(HttpEntity<MultiValueMap<String, String>> request, String clientId) {
        try {
            return restOperations.postForEntity(TOKEN_URI, request, String.class);
        } catch (HttpClientErrorException e) {
            String error = readError(e.getResponseBodyAsString());
            if ("invalid_grant".equals(error)) {
                // code 만료·재사용·다른 클라이언트에서 발급. 사용자가 다시 로그인하면 된다.
                log.warn("Apple code exchange rejected: error=invalid_grant, clientId={}", clientId);
                throw new AuthorizationCodeExchangeException(Reason.REJECTED, "invalid_grant", e);
            }
            // invalid_client 등은 키·Team ID·redirect_uri 같은 서버 설정 문제다.
            log.error("Apple code exchange failed: status={}, error={}, clientId={}",
                    e.getStatusCode().value(), error, clientId);
            throw new AuthorizationCodeExchangeException(Reason.FAILED, "Apple token endpoint error: " + error, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("Apple code exchange unavailable: {}", e.getClass().getSimpleName());
            throw new AuthorizationCodeExchangeException(Reason.UNAVAILABLE, "Apple token endpoint unavailable", e);
        }
    }

    private static String readRefreshToken(String body, String clientId) {
        Object refreshToken = parse(body).get("refresh_token");
        if (!(refreshToken instanceof String token) || token.isBlank()) {
            log.error("Apple code exchange returned no refresh_token, clientId={}", clientId);
            throw new AuthorizationCodeExchangeException(Reason.FAILED, "refresh_token is missing");
        }
        return token;
    }

    private static String readError(String body) {
        Object error = parse(body).get("error");
        return error instanceof String value ? value : "unknown";
    }

    private static Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return JSONObjectUtils.parse(body);
        } catch (ParseException e) {
            return Map.of();
        }
    }
}
