package com.danmalgi.backend.auth.infrastructure.oauth;

import com.danmalgi.backend.user.domain.model.OauthType;

/**
 * authorization code 를 refresh token 으로 바꾼다.
 *
 * <p>id_token 검증({@link OAuthPlatformAuthorizationPort})과 분리한 이유: 실패했을 때
 * 로그인을 막을지가 기존 유저인지에 따라 달라지고, 그 판단은 DB 를 보는 service 가 한다.
 * 구현체가 없는 제공자(Google)는 교환하지 않는다.
 */
public interface OAuthAuthorizationCodeExchangePort {
    OauthType supportedOauthType();

    /**
     * @param clientId        검증을 통과한 id_token 의 aud. code 를 발급받은 클라이언트와 같아야 한다.
     * @param expectedSubject 검증을 통과한 id_token 의 sub. code 가 같은 계정의 것인지 확인하는 데 쓴다.
     *                        다르면 다른 계정의 refresh token 이 이 계정에 저장된다.
     * @return refresh token 평문. 호출부가 바로 암호화한다.
     * @throws com.danmalgi.backend.auth.domain.exception.AuthorizationCodeExchangeException 교환 실패.
     *         계정 불일치는 {@code SUBJECT_MISMATCH}
     */
    String exchange(String authorizationCode, String clientId, String expectedSubject);
}
