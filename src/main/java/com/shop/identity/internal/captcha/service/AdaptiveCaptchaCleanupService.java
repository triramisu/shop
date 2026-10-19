package com.shop.identity.internal.captcha.service;

import com.shop.identity.internal.captcha.repository.CaptchaChallengeRepository;
import com.shop.identity.internal.captcha.repository.LoginFailureRepository;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdaptiveCaptchaCleanupService {

    CaptchaChallengeRepository captchaChallengeRepository;
    LoginFailureRepository loginFailureRepository;

    @Scheduled(
            fixedDelayString = "${app.security.captcha.cleanup-interval:60000}",
            initialDelayString = "${app.security.captcha.cleanup-initial-delay:60000}")
    @Transactional
    public void cleanupExpiredState() {
        Instant cutoff = Instant.now();
        int deletedChallenges = captchaChallengeRepository.deleteExpiredChallenges(cutoff);
        int deletedFailures = loginFailureRepository.deleteExpiredFailures(cutoff);
        if (deletedChallenges + deletedFailures > 0) {
            log.info(
                    "Deleted {} expired captcha challenges and {} expired login failure records",
                    deletedChallenges,
                    deletedFailures);
        }
    }
}
