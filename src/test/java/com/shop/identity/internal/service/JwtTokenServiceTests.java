package com.shop.identity.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shop.identity.internal.repository.RefreshTokenRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTests {

    private static final String SIGNER_KEY =
            "test-only-hs512-key-with-at-least-sixty-four-bytes-0123456789-abcdefghijk";
    private static final String OTHER_SIGNER_KEY =
            "different-test-only-hs512-key-with-at-least-sixty-four-bytes-abcdefghijk";
    private static final String ISSUER = "shop-test";
    private static final String AUDIENCE = "shop-api-test";

    private RefreshTokenRepository refreshTokenRepository;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        jwtTokenService = new JwtTokenService(refreshTokenRepository);
        configure(SIGNER_KEY, 3600, 36000);
    }

    @Test
    void validatesSignerStrengthAndTokenDurationsAtStartup() {
        assertThatCode(jwtTokenService::validateConfiguration).doesNotThrowAnyException();

        configure("short-key", 3600, 36000);
        assertThatThrownBy(jwtTokenService::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 64");

        configure(SIGNER_KEY, 0, 36000);
        assertThatThrownBy(jwtTokenService::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT durations are invalid");

        configure(SIGNER_KEY, 3600, 3600);
        assertThatThrownBy(jwtTokenService::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT durations are invalid");
    }

    @Test
    void acceptsOnlyTheExpectedTokenType() throws Exception {
        SignedJWT valid = jwtTokenService.verifyRefreshToken(validToken("refresh"));
        assertThat(valid.getJWTClaimsSet().getSubject()).isEqualTo("jwt-owner");

        assertTokenError(validToken("access"), ErrorCode.TOKEN_TYPE_INVALID);
    }

    @Test
    void rejectsInvalidAlgorithmsSignaturesAndSecurityClaims() throws Exception {
        Instant now = Instant.now();
        assertInvalid(token(
                JWSAlgorithm.HS256,
                SIGNER_KEY,
                ISSUER,
                List.of(AUDIENCE),
                now.minusSeconds(1),
                now.plusSeconds(60),
                "jwt-owner",
                "jwt-id",
                "refresh"));
        assertInvalid(token(
                JWSAlgorithm.HS512,
                OTHER_SIGNER_KEY,
                ISSUER,
                List.of(AUDIENCE),
                now.minusSeconds(1),
                now.plusSeconds(60),
                "jwt-owner",
                "jwt-id",
                "refresh"));
        assertInvalid(token(
                JWSAlgorithm.HS512,
                SIGNER_KEY,
                "other-issuer",
                List.of(AUDIENCE),
                now.minusSeconds(1),
                now.plusSeconds(60),
                "jwt-owner",
                "jwt-id",
                "refresh"));
        assertInvalid(token(
                JWSAlgorithm.HS512,
                SIGNER_KEY,
                ISSUER,
                List.of("other-audience"),
                now.minusSeconds(1),
                now.plusSeconds(60),
                "jwt-owner",
                "jwt-id",
                "refresh"));
        assertInvalid(token(
                JWSAlgorithm.HS512,
                SIGNER_KEY,
                ISSUER,
                List.of(AUDIENCE),
                now.minusSeconds(120),
                now.minusSeconds(1),
                "jwt-owner",
                "jwt-id",
                "refresh"));
        assertInvalid(token(
                JWSAlgorithm.HS512,
                SIGNER_KEY,
                ISSUER,
                List.of(AUDIENCE),
                now.plusSeconds(60),
                now.plusSeconds(120),
                "jwt-owner",
                "jwt-id",
                "refresh"));
        assertInvalid(token(
                JWSAlgorithm.HS512,
                SIGNER_KEY,
                ISSUER,
                List.of(AUDIENCE),
                now.minusSeconds(1),
                now.plusSeconds(60),
                null,
                "jwt-id",
                "refresh"));
        assertInvalid(token(
                JWSAlgorithm.HS512,
                SIGNER_KEY,
                ISSUER,
                List.of(AUDIENCE),
                now.minusSeconds(1),
                now.plusSeconds(60),
                "jwt-owner",
                null,
                "refresh"));
    }

    @Test
    void rejectsAnAccessTokenAfterItsServerSideFamilyIsRevoked() throws Exception {
        String accessToken = validToken("access");
        when(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNull("token-family"))
                .thenReturn(true, false);

        assertThat(jwtTokenService
                        .verifyAccessToken(accessToken)
                        .getJWTClaimsSet()
                        .getSubject())
                .isEqualTo("jwt-owner");
        assertThatThrownBy(() -> jwtTokenService.verifyAccessToken(accessToken))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    private void configure(String signerKey, long validDuration, long refreshableDuration) {
        ReflectionTestUtils.setField(jwtTokenService, "signerKey", signerKey);
        ReflectionTestUtils.setField(jwtTokenService, "validDuration", validDuration);
        ReflectionTestUtils.setField(jwtTokenService, "refreshableDuration", refreshableDuration);
        ReflectionTestUtils.setField(jwtTokenService, "issuer", ISSUER);
        ReflectionTestUtils.setField(jwtTokenService, "audience", AUDIENCE);
    }

    private String validToken(String tokenType) throws Exception {
        Instant now = Instant.now();
        return token(
                JWSAlgorithm.HS512,
                SIGNER_KEY,
                ISSUER,
                List.of(AUDIENCE),
                now.minusSeconds(1),
                now.plusSeconds(60),
                "jwt-owner",
                "jwt-id",
                tokenType);
    }

    private String token(
            JWSAlgorithm algorithm,
            String signingKey,
            String issuer,
            List<String> audience,
            Instant notBefore,
            Instant expiresAt,
            String subject,
            String jti,
            String tokenType)
            throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(notBefore))
                .notBeforeTime(Date.from(notBefore))
                .expirationTime(Date.from(expiresAt))
                .claim(JwtTokenService.TOKEN_TYPE_CLAIM, tokenType)
                .claim(JwtTokenService.FAMILY_ID_CLAIM, "token-family");
        if (subject != null) {
            claims.subject(subject);
        }
        if (jti != null) {
            claims.jwtID(jti);
        }

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(algorithm), claims.build());
        signedJwt.sign(new MACSigner(signingKey.getBytes(StandardCharsets.UTF_8)));
        return signedJwt.serialize();
    }

    private void assertInvalid(String token) {
        assertTokenError(token, ErrorCode.INVALID_TOKEN);
    }

    private void assertTokenError(String token, ErrorCode expectedError) {
        assertThatThrownBy(() -> jwtTokenService.verifyRefreshToken(token))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expectedError));
    }
}
