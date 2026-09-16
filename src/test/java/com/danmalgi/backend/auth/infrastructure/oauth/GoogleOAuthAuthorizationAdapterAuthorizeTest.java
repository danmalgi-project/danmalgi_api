package com.danmalgi.backend.auth.infrastructure.oauth;

import com.danmalgi.backend.auth.domain.exception.OauthAuthorizeFailException;
import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.oauth.impl.GoogleOAuthAuthorizationAdapter;
import com.danmalgi.backend.user.domain.model.OauthType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleOAuthAuthorizationAdapterAuthorizeTest {

    private static final byte[] TEST_SECRET = "test-secret-key-for-unit-tests-only!!".getBytes();

    private GoogleOAuthAuthorizationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new GoogleOAuthAuthorizationAdapter();
    }

    @Test
    void authorize_정상_idToken으로_PendingOAuthProfile_반환() throws Exception {
        String idToken = createJwt("google-sub-123", "test@gmail.com", "TestUser");

        PendingOAuthProfile result = adapter.authorize(idToken);

        assertThat(result.getIdentifyId()).isEqualTo("google-sub-123");
        assertThat(result.getEmail()).isEqualTo("test@gmail.com");
        assertThat(result.getOauthType()).isEqualTo(OauthType.GOOGLE.getNumber());
        // userId 는 AuthService 가 users 시퀀스에서 확보해 채운다.
        assertThat(result.getUserId()).isNull();
        // Google picture 클레임을 읽지 않으므로 Authorization 경로에서는 항상 null 이다.
        assertThat(result.getProfileImageUrl()).isNull();
    }

    @Test
    void authorize_name_클레임이_없어도_정상_반환한다() throws Exception {
        // name 은 profile 스코프가 있어야 실린다. normalizeName 제거로 소비자가 0개가 됐으므로
        // 쓰지도 않는 필드가 없다는 이유로 로그인을 거부하지 않는다 (이슈 #37 요구사항 3).
        String idToken = createJwtWithoutName("google-sub-123", "test@gmail.com");

        assertThatCode(() -> adapter.authorize(idToken)).doesNotThrowAnyException();
    }

    @Test
    void authorize_sub_없는_토큰이면_예외발생() throws Exception {
        String idToken = createJwtWithoutSub("test@gmail.com", "TestUser");

        assertThatThrownBy(() -> adapter.authorize(idToken))
                .isInstanceOf(OauthAuthorizeFailException.class);
    }

    @Test
    void authorize_email_없는_토큰이면_예외발생() throws Exception {
        // UserEntity.email 이 @NotNull 이라 email 검증은 유지한다.
        String idToken = createJwtWithoutEmail("google-sub-123", "TestUser");

        assertThatThrownBy(() -> adapter.authorize(idToken))
                .isInstanceOf(OauthAuthorizeFailException.class);
    }

    @Test
    void authorize_JWT_파싱_불가_토큰이면_예외발생() {
        String invalidToken = "not.a.valid.jwt.token";

        assertThatThrownBy(() -> adapter.authorize(invalidToken))
                .isInstanceOf(OauthAuthorizeFailException.class);
    }

    @Test
    void authorize_어댑터가_name과_tag를_생성하지_않는다() {
        // 해시 기반 tag 가 uk_users_name_tag 를 선점해 신규 로그인이 실패한 것이 이슈 #37 의
        // 직접 원인이다. 두 헬퍼가 되살아나면 같은 회귀가 재발한다.
        List<String> declaredMethods = Arrays.stream(GoogleOAuthAuthorizationAdapter.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(declaredMethods).doesNotContain("normalizeName", "generateTag");
    }

    private String createJwt(String sub, String email, String name) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .claim("email", email)
                .claim("name", name)
                .build();
        return signAndSerialize(claims);
    }

    private String createJwtWithoutSub(String email, String name) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("email", email)
                .claim("name", name)
                .build();
        return signAndSerialize(claims);
    }

    private String createJwtWithoutEmail(String sub, String name) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .claim("name", name)
                .build();
        return signAndSerialize(claims);
    }

    private String createJwtWithoutName(String sub, String email) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .claim("email", email)
                .build();
        return signAndSerialize(claims);
    }

    private String signAndSerialize(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(TEST_SECRET));
        return jwt.serialize();
    }
}
