package com.danmalgi.backend.auth.domain.model;

import com.danmalgi.backend.global.infrastructure.redis.RedisConfig;
import com.danmalgi.backend.user.domain.model.OauthType;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * pending 프로필이 실제 RedisTemplate 직렬화기로 왕복되는지 고정한다.
 *
 * <p>PendingAuthStore 테스트는 RedisTemplate 을 목으로 두므로 직렬화를 거치지 않는다.
 * oauthType 이 int 에서 enum 으로 바뀌었고, default typing 에서 enum 이 그대로 돌아오는지는
 * 여기서만 확인된다. 깨지면 Register 가 pending 세션을 찾지 못한다.
 */
class PendingOAuthProfileRedisSerializationTest {

    @SuppressWarnings("unchecked")
    private RedisSerializer<Object> valueSerializer() {
        return (RedisSerializer<Object>) new RedisConfig()
                .redisTemplate(mock(RedisConnectionFactory.class))
                .getValueSerializer();
    }

    @Test
    void pending_프로필은_oauthType_enum까지_그대로_왕복된다() {
        PendingOAuthProfile profile = PendingOAuthProfile.builder()
                .userId(100L)
                .email("abc@privaterelay.appleid.com")
                .identifyId("apple-sub-001")
                .oauthType(OauthType.APPLE)
                .oauthClientId("com.danmalgi.mobile")
                .encryptedRefreshToken("v1:encrypted")
                .build();
        RedisSerializer<Object> serializer = valueSerializer();

        Object restored = serializer.deserialize(serializer.serialize(profile));

        assertThat(restored).isInstanceOf(PendingOAuthProfile.class);
        PendingOAuthProfile result = (PendingOAuthProfile) restored;
        assertThat(result.getUserId()).isEqualTo(100L);
        assertThat(result.getOauthType()).isEqualTo(OauthType.APPLE);
        assertThat(result.getIdentifyId()).isEqualTo("apple-sub-001");
        assertThat(result.getOauthClientId()).isEqualTo("com.danmalgi.mobile");
        assertThat(result.getEncryptedRefreshToken()).isEqualTo("v1:encrypted");
    }
}
