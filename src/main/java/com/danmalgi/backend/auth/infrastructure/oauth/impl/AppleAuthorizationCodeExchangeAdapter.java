package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException;
import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException.Reason;
import com.danmalgi.backend.auth.infrastructure.oauth.OAuthAuthorizationCodeExchangePort;
import com.danmalgi.backend.auth.infrastructure.oauth.impl.AppleIdTokenVerificationException.Kind;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class AppleAuthorizationCodeExchangeAdapter implements OAuthAuthorizationCodeExchangePort {
    static final String TOKEN_URI = "https://appleid.apple.com/auth/token";

    private final RestOperations restOperations;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final AppleIdTokenVerifier idTokenVerifier;
    private final String webClientId;
    private final String redirectUri;

    @Autowired
    public AppleAuthorizationCodeExchangeAdapter(
            AppleClientSecretGenerator clientSecretGenerator,
            AppleIdTokenVerifier idTokenVerifier,
            @Value("${oauth.apple.web-client-id}") String webClientId,
            @Value("${oauth.apple.redirect-uri}") String redirectUri
    ) {
        this(createRestOperations(), clientSecretGenerator, idTokenVerifier, webClientId, redirectUri);
    }

    AppleAuthorizationCodeExchangeAdapter(
            RestOperations restOperations,
            AppleClientSecretGenerator clientSecretGenerator,
            AppleIdTokenVerifier idTokenVerifier,
            String webClientId,
            String redirectUri
    ) {
        this.restOperations = restOperations;
        this.clientSecretGenerator = clientSecretGenerator;
        this.idTokenVerifier = idTokenVerifier;
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
    public String exchange(String authorizationCode, String clientId, String expectedSubject) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode);
        form.add("client_id", clientId);
        form.add("client_secret", generateClientSecret(clientId));
        // Android 는 웹 플로우(Services ID)로 code 를 받는다. 웹 플로우 code 는 발급 때 쓴
        // redirect_uri 를 같이 보내야 교환된다. iOS 네이티브(Bundle ID)는 보내면 안 된다.
        if (webClientId.equals(clientId)) {
            form.add("redirect_uri", redirectUri);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = post(new HttpEntity<>(form, headers), clientId);
        Map<String, Object> body = parse(response.getBody());
        // refresh token 을 읽기 전에 그것이 누구의 것인지부터 확인한다.
        verifySubject(body, clientId, expectedSubject);
        return readRefreshToken(body, clientId);
    }

    private String generateClientSecret(String clientId) {
        try {
            return clientSecretGenerator.generate(clientId);
        } catch (IllegalStateException e) {
            // 서명 실패는 키 설정 문제다. 전역 핸들러의 FAILED_PRECONDITION + 메시지 노출을 타지 않게 감싼다.
            log.error("Apple client_secret generation failed, clientId={}", clientId);
            throw new AuthorizationCodeExchangeException(Reason.FAILED, "client_secret generation failed", e);
        }
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
        } catch (RestClientException e) {
            // 알 수 없는 status 코드 등. 메시지에 응답 본문이 섞일 수 있어 그대로 내보내지 않는다.
            log.error("Apple code exchange unavailable: {}", e.getClass().getSimpleName());
            throw new AuthorizationCodeExchangeException(Reason.UNAVAILABLE, "Apple token endpoint unavailable", e);
        }
    }

    /**
     * 응답 id_token 은 Apple 이 이 code 를 누구에게 발급했는지 알려 주는 유일한 근거다.
     * 클라이언트가 보낸 idToken 과 code 는 따로 오므로, 계정 A 의 idToken 에 계정 B 의 code 를
     * 섞으면 B 의 refresh token 이 A 에 저장되고 탈퇴 때 엉뚱한 토큰을 revoke 하게 된다.
     */
    private void verifySubject(Map<String, Object> body, String clientId, String expectedSubject) {
        if (!(body.get("id_token") instanceof String idToken) || idToken.isBlank()) {
            log.error("Apple code exchange returned no id_token, clientId={}", clientId);
            throw new AuthorizationCodeExchangeException(Reason.FAILED, "id_token is missing");
        }

        Jwt jwt;
        try {
            // aud 는 요청에 쓴 client_id 하나만 허용한다.
            jwt = idTokenVerifier.verify(idToken, Set.of(clientId));
        } catch (AppleIdTokenVerificationException e) {
            if (e.kind() == Kind.UNAVAILABLE) {
                log.error("Apple code exchange id_token verification unavailable: {}", e.getMessage());
                throw new AuthorizationCodeExchangeException(Reason.UNAVAILABLE, "Apple JWKS unavailable", e);
            }
            // Apple 이 TLS 로 준 응답이 검증에 실패하는 것은 설정(client_id) 문제이거나 정상이 아닌 경로다.
            log.error("Apple code exchange returned invalid id_token: {}, clientId={}", e.getMessage(), clientId);
            throw new AuthorizationCodeExchangeException(Reason.FAILED, "id_token is invalid", e);
        }

        if (!expectedSubject.equals(jwt.getSubject())) {
            // sub 는 남기지 않는다. 다른 계정의 code 를 섞으려는 시도일 수 있으므로 error 로 남긴다.
            log.error("Apple code exchange rejected: subject mismatch, clientId={}", clientId);
            throw new AuthorizationCodeExchangeException(Reason.SUBJECT_MISMATCH, "subject mismatch");
        }
    }

    private static String readRefreshToken(Map<String, Object> body, String clientId) {
        Object refreshToken = body.get("refresh_token");
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
