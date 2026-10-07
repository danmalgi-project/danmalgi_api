package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException;
import com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException.Reason;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestOperations;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppleAuthorizationCodeExchangeAdapterExchangeTest {

    private static final String IOS_CLIENT_ID = "com.danmalgi.mobile";
    private static final String WEB_CLIENT_ID = "com.danmalgi.mobile.service";
    private static final String REDIRECT_URI = "https://danmalgi-api.hyang.su/auth/apple/callback";
    private static final String TOKEN_RESPONSE = """
            {"access_token":"at","token_type":"Bearer","expires_in":3600,
             "refresh_token":"apple-refresh-token","id_token":"x.y.z"}""";

    private RestOperations restOperations;
    private AppleClientSecretGenerator clientSecretGenerator;
    private AppleAuthorizationCodeExchangeAdapter adapter;

    @BeforeEach
    void setUp() {
        restOperations = mock(RestOperations.class);
        clientSecretGenerator = mock(AppleClientSecretGenerator.class);
        when(clientSecretGenerator.generate(any())).thenReturn("client-secret-jwt");
        adapter = new AppleAuthorizationCodeExchangeAdapter(
                restOperations, clientSecretGenerator, WEB_CLIENT_ID, REDIRECT_URI);
    }

    @Test
    void exchange_응답의_refresh_token을_반환한다() {
        givenResponse(TOKEN_RESPONSE);

        assertThat(adapter.exchange("auth-code", IOS_CLIENT_ID)).isEqualTo("apple-refresh-token");
    }

    @Test
    void exchange_iOS_client_id면_redirect_uri_없이_form으로_요청한다() {
        givenResponse(TOKEN_RESPONSE);

        adapter.exchange("auth-code", IOS_CLIENT_ID);

        HttpEntity<MultiValueMap<String, String>> request = capturedRequest();
        assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = request.getBody();
        assertThat(form.getFirst("grant_type")).isEqualTo("authorization_code");
        assertThat(form.getFirst("code")).isEqualTo("auth-code");
        assertThat(form.getFirst("client_id")).isEqualTo(IOS_CLIENT_ID);
        assertThat(form.getFirst("client_secret")).isEqualTo("client-secret-jwt");
        assertThat(form.containsKey("redirect_uri")).isFalse();
    }

    @Test
    void exchange_web_client_id면_redirect_uri를_함께_보낸다() {
        givenResponse(TOKEN_RESPONSE);

        adapter.exchange("auth-code", WEB_CLIENT_ID);

        assertThat(capturedRequest().getBody().getFirst("redirect_uri")).isEqualTo(REDIRECT_URI);
    }

    @Test
    void exchange_client_secret은_인자로_받은_client_id로_만든다() {
        givenResponse(TOKEN_RESPONSE);

        adapter.exchange("auth-code", WEB_CLIENT_ID);

        // sub 가 code 를 발급받은 클라이언트와 다르면 Apple 이 invalid_client 로 거절한다.
        verify(clientSecretGenerator).generate(WEB_CLIENT_ID);
    }

    @Test
    void exchange_invalid_grant면_REJECTED() {
        givenClientError("{\"error\":\"invalid_grant\"}");

        assertFailure(Reason.REJECTED, Status.UNAUTHENTICATED);
    }

    @Test
    void exchange_invalid_client면_FAILED() {
        givenClientError("{\"error\":\"invalid_client\"}");

        assertFailure(Reason.FAILED, Status.INTERNAL);
    }

    @Test
    void exchange_Apple_5xx면_UNAVAILABLE() {
        when(restOperations.postForEntity(eq(AppleAuthorizationCodeExchangeAdapter.TOKEN_URI), any(), eq(String.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertFailure(Reason.UNAVAILABLE, Status.UNAVAILABLE);
    }

    @Test
    void exchange_연결_실패면_UNAVAILABLE() {
        when(restOperations.postForEntity(eq(AppleAuthorizationCodeExchangeAdapter.TOKEN_URI), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));

        assertFailure(Reason.UNAVAILABLE, Status.UNAVAILABLE);
    }

    @Test
    void exchange_응답에_refresh_token이_없으면_FAILED() {
        givenResponse("{\"access_token\":\"at\",\"id_token\":\"x.y.z\"}");

        assertFailure(Reason.FAILED, Status.INTERNAL);
    }

    private void givenResponse(String body) {
        when(restOperations.postForEntity(eq(AppleAuthorizationCodeExchangeAdapter.TOKEN_URI), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    private void givenClientError(String body) {
        when(restOperations.postForEntity(eq(AppleAuthorizationCodeExchangeAdapter.TOKEN_URI), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                        HttpHeaders.EMPTY, body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private HttpEntity<MultiValueMap<String, String>> capturedRequest() {
        ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restOperations).postForEntity(eq(AppleAuthorizationCodeExchangeAdapter.TOKEN_URI), captor.capture(), eq(String.class));
        return captor.getValue();
    }

    private void assertFailure(Reason reason, Status status) {
        assertThatThrownBy(() -> adapter.exchange("auth-code", IOS_CLIENT_ID))
                .isInstanceOfSatisfying(AuthorizationCodeExchangeException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(reason);
                    assertThat(e.getStatus()).isEqualTo(status);
                });
    }
}
