package com.shop.identity.internal.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shop.identity.internal.service.JwtTokenService;
import com.shop.shared.error.AppException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomJwtDecoder implements JwtDecoder {

    JwtTokenService jwtTokenService;

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            SignedJWT signedJwt = jwtTokenService.verifyAccessToken(token);
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            Map<String, Object> convertedClaims = new HashMap<>(claims.getClaims());
            convertedClaims.put("iat", claims.getIssueTime().toInstant());
            convertedClaims.put("nbf", claims.getNotBeforeTime().toInstant());
            convertedClaims.put("exp", claims.getExpirationTime().toInstant());

            return Jwt.withTokenValue(token)
                    .headers(headers -> headers.putAll(signedJwt.getHeader().toJSONObject()))
                    .claims(jwtClaims -> jwtClaims.putAll(convertedClaims))
                    .issuedAt(claims.getIssueTime().toInstant())
                    .expiresAt(claims.getExpirationTime().toInstant())
                    .build();
        } catch (AppException | JOSEException | ParseException exception) {
            throw new BadJwtException("Token validation failed", exception);
        }
    }
}
