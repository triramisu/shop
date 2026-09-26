package com.shop.identity.internal.captcha.service;

import com.shop.identity.internal.captcha.configuration.AdaptiveCaptchaProperties;
import com.shop.identity.internal.captcha.dto.response.CaptchaResponse;
import com.shop.identity.internal.captcha.entity.CaptchaChallenge;
import com.shop.identity.internal.captcha.entity.LoginFailure;
import com.shop.identity.internal.captcha.repository.CaptchaChallengeRepository;
import com.shop.identity.internal.captcha.repository.LoginFailureRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdaptiveCaptchaService {

    LoginFailureRepository loginFailureRepository;
    CaptchaChallengeRepository captchaChallengeRepository;
    AdaptiveCaptchaProperties properties;
    CaptchaImageGenerator imageGenerator;

    @Transactional
    public CaptchaResponse issueChallenge(String username) {
        if (!properties.isEnabled()) {
            throw new AppException(ErrorCode.CAPTCHA_UNAVAILABLE);
        }

        Instant now = Instant.now();
        String principalHash = hash(normalizeUsername(username));
        LoginFailure failure = loginFailureRepository.findById(principalHash).orElse(null);
        if (failure == null || !failure.requiresCaptcha(properties.getFailureThreshold(), now)) {
            throw new AppException(ErrorCode.CAPTCHA_NOT_REQUIRED);
        }

        captchaChallengeRepository.acquireIssuanceLock();
        captchaChallengeRepository.deleteExpiredChallenges(now);
        captchaChallengeRepository.deleteByPrincipalHash(principalHash);
        if (captchaChallengeRepository.countByExpiresAtAfter(now) >= properties.getMaxActiveChallenges()) {
            throw new AppException(ErrorCode.CAPTCHA_UNAVAILABLE);
        }

        String captchaId = UUID.randomUUID().toString();
        CaptchaImageGenerator.GeneratedCaptcha generatedCaptcha = imageGenerator.generate();
        Instant expiresAt = now.plus(properties.getChallengeTtl());
        CaptchaChallenge challenge = CaptchaChallenge.issue(
                captchaId, principalHash, hashAnswer(captchaId, generatedCaptcha.answer()), expiresAt, now);
        captchaChallengeRepository.saveAndFlush(challenge);

        return CaptchaResponse.builder()
                .captchaId(captchaId)
                .imageData(generatedCaptcha.imageData())
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional(noRollbackFor = AppException.class)
    public void verifyIfRequired(String username, String captchaId, String captchaAnswer) {
        if (!properties.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        String principalHash = hash(normalizeUsername(username));
        LoginFailure failure = loginFailureRepository.findById(principalHash).orElse(null);
        if (failure == null) {
            return;
        }
        if (!failure.requiresCaptcha(properties.getFailureThreshold(), now)) {
            if (!failure.requiresCaptcha(1, now)) {
                loginFailureRepository.delete(failure);
            }
            return;
        }
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaAnswer)) {
            throw new AppException(ErrorCode.CAPTCHA_REQUIRED);
        }

        CaptchaChallenge challenge = captchaChallengeRepository
                .findByCaptchaIdForUpdate(captchaId.strip())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CAPTCHA));
        captchaChallengeRepository.delete(challenge);
        captchaChallengeRepository.flush();

        String submittedAnswerHash = hashAnswer(captchaId.strip(), captchaAnswer);
        boolean valid = !challenge.isExpired(now)
                && MessageDigest.isEqual(
                        challenge.getPrincipalHash().getBytes(StandardCharsets.US_ASCII),
                        principalHash.getBytes(StandardCharsets.US_ASCII))
                && MessageDigest.isEqual(
                        challenge.getAnswerHash().getBytes(StandardCharsets.US_ASCII),
                        submittedAnswerHash.getBytes(StandardCharsets.US_ASCII));
        if (!valid) {
            throw new AppException(ErrorCode.INVALID_CAPTCHA);
        }
    }

    @Transactional
    public void recordFailure(String username) {
        if (!properties.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        String principalHash = hash(normalizeUsername(username));
        loginFailureRepository.recordFailure(principalHash, now, now.plus(properties.getFailureWindow()));
    }

    @Transactional
    public void clearFailures(String username) {
        if (properties.isEnabled()) {
            loginFailureRepository.deleteById(hash(normalizeUsername(username)));
        }
    }

    private String normalizeUsername(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    private String hashAnswer(String captchaId, String answer) {
        return hash(captchaId + ':' + answer.strip().toUpperCase(Locale.ROOT));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
