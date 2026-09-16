package com.danmalgi.backend.auth.infrastructure.oauth;

import com.danmalgi.backend.auth.domain.model.PendingOAuthProfile;
import com.danmalgi.backend.auth.infrastructure.oauth.impl.KakaoOAuthAuthorizationAdapter;
import com.danmalgi.backend.user.domain.model.OauthType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoOAuthAuthorizationAdapterAuthorizeTest {

    private KakaoOAuthAuthorizationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new KakaoOAuthAuthorizationAdapter();
    }

    @Test
    void authorize_미구현_상태를_유지해_null을_반환한다() {
        // 반환 타입만 PendingOAuthProfile 로 바뀐다. null 반환은 그대로이고,
        // AuthService.authorize 가 이 null 을 OauthAuthorizeFailException 으로 바꾼다
        // (AuthServiceAuthorizeTest.authorize_OAuthPort가_null_반환하면_예외발생).
        PendingOAuthProfile result = adapter.authorize("id-token");

        assertThat(result).isNull();
    }

    @Test
    void supportedOauthType은_KAKAO다() {
        assertThat(adapter.supportedOauthType()).isEqualTo(OauthType.KAKAO);
    }
}
