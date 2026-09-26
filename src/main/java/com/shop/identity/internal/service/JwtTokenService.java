package com.shop.identity.internal.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shop.identity.internal.entity.Permission;
import com.shop.identity.internal.entity.Role;
import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.repository.RefreshTokenRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtTokenService {

    static final String TOKEN_TYPE_CLAIM = "token_type";
    static final String ACCESS_TOKEN_TYPE = "access";
    static final String REFRESH_TOKEN_TYPE = "refresh";
    static final String FAMILY_ID_CLAIM = "family_id";

    RefreshTokenRepository refreshTokenRepository;

    @NonFinal
    @Value("${jwt.signerKey}")
    String signerKey;

    @NonFinal
    @Value("${jwt.valid-duration}")
    long validDuration;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    long refreshableDuration;

    @NonFinal
    @Value("${jwt.issuer:shop}")
    String issuer;

    @NonFinal
    @Value("${jwt.audience:shop-api}")
    String audience;

    @PostConstruct
    void validateConfiguration() {
        if (signerKey == null || signerKey.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalStateException("JWT_SIGNER_KEY must contain at least 64 UTF-8 bytes for HS512");
        }
        if (validDuration <= 0 || refreshableDuration <= validDuration) {
            throw new IllegalStateException("JWT durations are invalid");
        }
    }

    IssuedTokenPair issue(User user, String existingFamilyId) {
        Instant now = Instant.now();
        String familyId = existingFamilyId == null ? UUID.randomUUID().toString() : existingFamilyId;
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();
        Instant accessExpiry = now.plus(validDuration, ChronoUnit.SECONDS);
        Instant refreshExpiry = now.plus(refreshableDuration, ChronoUnit.SECONDS);

        String accessToken = sign(user, accessJti, ACCESS_TOKEN_TYPE, familyId, now, accessExpiry, buildScope(user));
        String refreshToken = sign(user, refreshJti, REFRESH_TOKEN_TYPE, familyId, now, refreshExpiry, null);

        return IssuedTokenPair.builder()
                .accessToken(accessToken)
                .accessTokenExpiresAt(accessExpiry)
                .refreshToken(refreshToken)
                .refreshJti(refreshJti)
                .familyId(familyId)
                .refreshTokenExpiresAt(refreshExpiry)
                .issuedAt(now)
                .build();
    }

    public SignedJWT verifyAccessToken(String token) throws ParseException, JOSEException {
        SignedJWT signedJwt = verifyToken(token, ACCESS_TOKEN_TYPE);
        String familyId = signedJwt.getJWTClaimsSet().getStringClaim(FAMILY_ID_CLAIM);
        if (familyId == null || !refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNull(familyId)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        return signedJwt;
    }

    SignedJWT verifyRefreshToken(String token) throws ParseException, JOSEException {
        return verifyToken(token, REFRESH_TOKEN_TYPE);
    }

    String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String sign(
            User user,
            String jti,
            String tokenType,
            String familyId,
            Instant issuedAt,
            Instant expiresAt,
            String scope) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(jti)
                .claim(TOKEN_TYPE_CLAIM, tokenType);

        if (scope != null) {
            claims.claim("scope", scope);
        }
        if (familyId != null) {
            claims.claim(FAMILY_ID_CLAIM, familyId);
        }

        JWSObject jwsObject = new JWSObject(
                new JWSHeader(JWSAlgorithm.HS512), new Payload(claims.build().toJSONObject()));
        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes(StandardCharsets.UTF_8)));
            return jwsObject.serialize();
        } catch (JOSEException exception) {
            log.error("Cannot create JWT", exception);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    private SignedJWT verifyToken(String token, String expectedType) throws ParseException, JOSEException {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS512.equals(signedJwt.getHeader().getAlgorithm())) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            boolean verified = signedJwt.verify(new MACVerifier(signerKey.getBytes(StandardCharsets.UTF_8)));
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            Instant now = Instant.now();

            if (!verified
                    || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant().isAfter(now)
                    || claims.getNotBeforeTime() == null
                    || claims.getNotBeforeTime().toInstant().isAfter(now)
                    || !issuer.equals(claims.getIssuer())
                    || !claims.getAudience().contains(audience)
                    || claims.getSubject() == null
                    || claims.getJWTID() == null) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            if (!expectedType.equals(claims.getStringClaim(TOKEN_TYPE_CLAIM))) {
                throw new AppException(ErrorCode.TOKEN_TYPE_INVALID);
            }
            return signedJwt;
        } catch (AppException exception) {
            throw exception;
        } catch (ParseException | JOSEException exception) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        } catch (RuntimeException exception) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    private String buildScope(User user) {
        StringJoiner scope = new StringJoiner(" ");
        Set<Role> roles = user.getRoles();
        if (!CollectionUtils.isEmpty(roles)) {
            roles.forEach(role -> {
                scope.add("ROLE_" + role.getCode());
                Set<Permission> permissions = role.getPermissions();
                if (!CollectionUtils.isEmpty(permissions)) {
                    permissions.stream().map(Permission::getCode).sorted().forEach(scope::add);
                }
            });
        }
        return scope.toString();
    }
}
