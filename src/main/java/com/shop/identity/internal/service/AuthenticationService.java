package com.shop.identity.internal.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.SignedJWT;
import com.shop.identity.internal.captcha.service.AdaptiveCaptchaService;
import com.shop.identity.internal.dto.request.AuthenticationRequest;
import com.shop.identity.internal.dto.request.IntrospectRequest;
import com.shop.identity.internal.dto.request.LogoutRequest;
import com.shop.identity.internal.dto.request.RefreshRequest;
import com.shop.identity.internal.dto.response.AuthenticationResponse;
import com.shop.identity.internal.dto.response.IntrospectResponse;
import com.shop.identity.internal.entity.RefreshToken;
import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.entity.UserStatus;
import com.shop.identity.internal.repository.RefreshTokenRepository;
import com.shop.identity.internal.repository.UserRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.text.ParseException;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {

    private static final String DUMMY_PASSWORD_HASH = "$2a$12$8431bqSxfDupMUkcYC7x4eaYVGBL77g1rC0l.SiACbtZP1PYaOXXG";

    UserRepository userRepository;
    RefreshTokenRepository refreshTokenRepository;
    PasswordEncoder passwordEncoder;
    JwtTokenService jwtTokenService;
    AdaptiveCaptchaService adaptiveCaptchaService;

    @Transactional(noRollbackFor = AppException.class)
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        String username = request.getUsername().strip();
        adaptiveCaptchaService.verifyIfRequired(username, request.getCaptchaId(), request.getCaptchaAnswer());

        var user = userRepository.findByUsernameIgnoreCase(username);
        String passwordHash = user.map(User::getPasswordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), passwordHash);

        if (user.isEmpty() || !passwordMatches) {
            adaptiveCaptchaService.recordFailure(username);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (user.get().getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_DISABLED);
        }

        adaptiveCaptchaService.clearFailures(username);
        return issueAndPersist(user.get(), null);
    }

    public IntrospectResponse introspect(IntrospectRequest request) {
        boolean valid = true;
        try {
            jwtTokenService.verifyAccessToken(request.getToken());
        } catch (AppException | JOSEException | ParseException exception) {
            valid = false;
        }
        return IntrospectResponse.builder().valid(valid).build();
    }

    @Transactional(noRollbackFor = AppException.class)
    public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
        SignedJWT signedJwt = jwtTokenService.verifyRefreshToken(request.getToken());
        String jti = signedJwt.getJWTClaimsSet().getJWTID();
        String subject = signedJwt.getJWTClaimsSet().getSubject();
        String familyId = signedJwt.getJWTClaimsSet().getStringClaim(JwtTokenService.FAMILY_ID_CLAIM);

        RefreshToken storedToken = refreshTokenRepository
                .findByJtiForUpdate(jti)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        if (!Objects.equals(familyId, storedToken.getFamilyId())
                || !Objects.equals(subject, storedToken.getUser().getUsername())
                || !jwtTokenService.hashToken(request.getToken()).equals(storedToken.getTokenHash())) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        Instant now = Instant.now();
        if (storedToken.isConsumed()) {
            refreshTokenRepository.revokeFamily(storedToken.getFamilyId(), now);
            throw new AppException(ErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (storedToken.isRevoked() || storedToken.isExpired(now)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        if (storedToken.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_DISABLED);
        }

        IssuedTokenPair tokenPair = jwtTokenService.issue(storedToken.getUser(), storedToken.getFamilyId());
        storedToken.consume(now, tokenPair.refreshJti());
        persistRefreshToken(storedToken.getUser(), tokenPair);

        return toResponse(tokenPair);
    }

    @Transactional
    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        SignedJWT signedJwt = jwtTokenService.verifyRefreshToken(request.getToken());
        String jti = signedJwt.getJWTClaimsSet().getJWTID();
        String subject = signedJwt.getJWTClaimsSet().getSubject();
        String familyId = signedJwt.getJWTClaimsSet().getStringClaim(JwtTokenService.FAMILY_ID_CLAIM);

        RefreshToken storedToken = refreshTokenRepository
                .findByJtiForUpdate(jti)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        if (!Objects.equals(familyId, storedToken.getFamilyId())
                || !Objects.equals(subject, storedToken.getUser().getUsername())
                || !jwtTokenService.hashToken(request.getToken()).equals(storedToken.getTokenHash())) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        refreshTokenRepository.revokeFamily(storedToken.getFamilyId(), Instant.now());
    }

    private AuthenticationResponse issueAndPersist(User user, String familyId) {
        IssuedTokenPair tokenPair = jwtTokenService.issue(user, familyId);
        persistRefreshToken(user, tokenPair);
        return toResponse(tokenPair);
    }

    private void persistRefreshToken(User user, IssuedTokenPair tokenPair) {
        RefreshToken refreshToken = RefreshToken.issue(
                tokenPair.refreshJti(),
                user,
                tokenPair.familyId(),
                jwtTokenService.hashToken(tokenPair.refreshToken()),
                tokenPair.refreshTokenExpiresAt(),
                tokenPair.issuedAt());
        refreshTokenRepository.saveAndFlush(refreshToken);
    }

    private AuthenticationResponse toResponse(IssuedTokenPair tokenPair) {
        return AuthenticationResponse.builder()
                .accessToken(tokenPair.accessToken())
                .refreshToken(tokenPair.refreshToken())
                .authenticated(true)
                .accessTokenExpiresAt(tokenPair.accessTokenExpiresAt())
                .refreshTokenExpiresAt(tokenPair.refreshTokenExpiresAt())
                .build();
    }
}
