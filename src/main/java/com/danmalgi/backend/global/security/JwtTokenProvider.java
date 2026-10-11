package com.danmalgi.backend.global.security;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Component
public class JwtTokenProvider {
    private static final int MIN_SECRET_KEY_LENGTH = 32;
    private final byte[] secretKey;

    public record JwtPayload(Long userId, String pendingSessionId, String deviceId) {}

    // yaml 추후 추가
    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        this.secretKey = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(Long userId, String deviceID) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (deviceID == null || deviceID.isBlank()) {
            throw new IllegalArgumentException("deviceID must not be blank");
        }
        if (secretKey.length < MIN_SECRET_KEY_LENGTH) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }

        Instant now = Instant.now();
        // Instant expiresAt = now.plusMillis(accessTokenExpirationMillis);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("Danmalgi")
                .claim("userId", userId)
                .claim("deviceId", deviceID)
                .issueTime(Date.from(now))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                claimsSet
        );

        try {
            signedJWT.sign(new MACSigner(secretKey));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate JWT token", e);
        }
    }

    /**
     * {@code users} 행이 아직 없는 pending 세션용 토큰. {@code userId} 클레임을 넣지 않는다 —
     * 인터셉터가 클레임 종류로 ACTIVE/pending 을 가른다 (userId 가 있으면 등록 완료 토큰).
     */
    public String generatePendingToken(String pendingSessionId, String deviceId) {
        if (pendingSessionId == null || pendingSessionId.isBlank()) {
            throw new IllegalArgumentException("pendingSessionId must not be blank");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        if (secretKey.length < MIN_SECRET_KEY_LENGTH) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("Danmalgi")
                .claim("pendingSessionId", pendingSessionId)
                .claim("deviceId", deviceId)
                .issueTime(Date.from(Instant.now()))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                claimsSet
        );

        try {
            signedJWT.sign(new MACSigner(secretKey));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate JWT token", e);
        }
    }

    public JwtPayload validateAndGetPayload(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }

        if (secretKey.length < MIN_SECRET_KEY_LENGTH) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            boolean verified = signedJWT.verify(new MACVerifier(secretKey));
            if (!verified) {
                throw new IllegalArgumentException("token signature is invalid");
            }

            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            Date expirationTime = claimsSet.getExpirationTime();
            if (expirationTime != null && expirationTime.before(new Date())) {
                throw new IllegalArgumentException("token expired");
            }

            Long userId = parseUserId(claimsSet.getClaim("userId"));
            String pendingSessionId = parsePendingSessionId(claimsSet.getClaim("pendingSessionId"));
            if (userId == null && pendingSessionId == null) {
                throw new IllegalArgumentException("token has neither userId nor pendingSessionId claim");
            }
            String deviceId = parseDeviceId(claimsSet.getClaim("deviceId"));
            return new JwtPayload(userId, pendingSessionId, deviceId);
        } catch (ParseException | JOSEException e) {
            throw new IllegalArgumentException("token is invalid", e);
        }
    }

    private Long parseUserId(Object userIdClaim) {
        if (userIdClaim instanceof Number numberValue) {
            return numberValue.longValue();
        }

        if (userIdClaim instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                // Fall through — 숫자가 아니면 userId 클레임이 없는 것으로 본다.
            }
        }

        return null;
    }

    private String parsePendingSessionId(Object pendingSessionIdClaim) {
        if (pendingSessionIdClaim instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }

        return null;
    }

    private String parseDeviceId(Object deviceIdClaim) {
        if (deviceIdClaim instanceof String deviceId && !deviceId.isBlank()) {
            return deviceId;
        }

        throw new IllegalArgumentException("deviceId claim is missing or invalid");
    }
}
