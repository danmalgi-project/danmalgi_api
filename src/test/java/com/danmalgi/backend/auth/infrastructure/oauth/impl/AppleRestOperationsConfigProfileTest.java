package com.danmalgi.backend.auth.infrastructure.oauth.impl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "운영에서는 Apple 호출을 다른 데로 돌릴 수 없다"를 고정하는 테스트다.
 * 프로필 분기를 잘못 건드리면 설정만으로 JWKS 신뢰 기점이 바뀌므로 구조를 못 박아 둔다.
 */
class AppleRestOperationsConfigProfileTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AppleRestOperationsConfig.class, LocalAppleRestOperationsConfig.class);

    @Test
    void appleRestOperations_프로필이_없으면_URI를_바꾸지_않는다() {
        contextRunner.run(context -> assertThat(requestFactoryOf(context.getBean(RestOperations.class)))
                .isNotInstanceOf(AppleLocalHostRewriteRequestFactory.class));
    }

    @Test
    void appleRestOperations_운영_프로필이면_URI를_바꾸지_않는다() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(requestFactoryOf(context.getBean(RestOperations.class)))
                        .isNotInstanceOf(AppleLocalHostRewriteRequestFactory.class));
    }

    @Test
    void appleRestOperations_local_프로필이면_URI를_바꿔치기한다() {
        contextRunner.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(requestFactoryOf(context.getBean(RestOperations.class)))
                        .isInstanceOf(AppleLocalHostRewriteRequestFactory.class));
    }

    @Test
    void appleRestOperations_프로필마다_빈이_하나씩만_등록된다() {
        contextRunner.run(context -> assertThat(context.getBeanNamesForType(RestOperations.class))
                .containsExactly("appleRestOperations"));
        contextRunner.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context.getBeanNamesForType(RestOperations.class))
                        .containsExactly("appleRestOperations"));
    }

    private static Object requestFactoryOf(RestOperations restOperations) {
        return ((RestTemplate) restOperations).getRequestFactory();
    }
}
