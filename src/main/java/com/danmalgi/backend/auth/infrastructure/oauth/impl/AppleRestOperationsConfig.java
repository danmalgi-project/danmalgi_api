package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestOperations;

/**
 * Apple 호출(JWKS 조회, authorization code 교환)에 쓸 {@code RestOperations}.
 *
 * <p>두 어댑터가 각자 {@code RestTemplate} 을 만들던 것을 빈 하나로 모았다. 이렇게 해야
 * local 프로필이 <b>이 빈만</b> 바꿔 끼워 로컬 가짜 Apple 서버를 볼 수 있다.
 * 설정 클래스를 인프라 클래스 옆에 두는 것은 {@code R2Config}/{@code RedisConfig} 와 같다.
 *
 * <p>Google 은 여전히 {@code OidcIdTokenVerifier.createRestOperations()} 를 직접 쓴다.
 * 바꿔치기 대상이 Apple 뿐이라 Google 까지 빈으로 끌어낼 이유가 없다.
 *
 * @see LocalAppleRestOperationsConfig local 프로필에서 이 빈을 대신하는 정의
 */
@Configuration
@Profile("!local")
class AppleRestOperationsConfig {
    @Bean("appleRestOperations")
    RestOperations appleRestOperations() {
        return OidcIdTokenVerifier.createRestOperations();
    }
}
