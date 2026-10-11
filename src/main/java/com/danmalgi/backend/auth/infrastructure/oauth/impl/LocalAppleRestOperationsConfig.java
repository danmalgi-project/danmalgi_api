package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * local 프로필에서만, Apple 호출을 로컬 가짜 서버({@code scripts/fake-apple.py})로 돌린다.
 * Apple id_token 을 발급받을 수 없는 환경에서 로그인 RPC 를 끝까지 돌려 보려는 것이다.
 *
 * <p>프로필이 갈라지는 지점을 설정 클래스 두 개로 나눈 이유: 같은 빈 이름을 서로 다른 프로필의
 * {@code @Bean} 메서드 둘에 붙이는 방식은 정의 등록 순서에 기대게 된다. 클래스 단위로 끊으면
 * 둘 중 하나만 아예 등록되지 않는다.
 *
 * <p><b>이 프로필이 운영에서 켜지면 Apple 인증이 통째로 가짜 서버로 넘어간다.</b>
 * 방어 수단은 기동 시 남기는 WARN 로그뿐이다.
 *
 * @see AppleLocalHostRewriteRequestFactory 상수를 설정값으로 빼지 않은 이유
 */
@Slf4j
@Configuration
@Profile("local")
class LocalAppleRestOperationsConfig {
    @Bean("appleRestOperations")
    RestOperations appleRestOperations(
            @Value("${oauth.apple.local.base-url:http://127.0.0.1:9099}") String baseUrl
    ) {
        log.warn("LOCAL PROFILE: Apple 호출을 {} 로 돌립니다. 운영에서 이 로그가 보이면 즉시 중단하세요.", baseUrl);
        return new RestTemplate(new AppleLocalHostRewriteRequestFactory(
                OidcIdTokenVerifier.createRequestFactory(), baseUrl));
    }
}
