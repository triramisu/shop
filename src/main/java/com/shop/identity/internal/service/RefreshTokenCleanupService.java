package com.shop.identity.internal.service;

import com.shop.identity.internal.repository.RefreshTokenRepository;
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
public class RefreshTokenCleanupService {

    RefreshTokenRepository refreshTokenRepository;

    @Scheduled(
            fixedDelayString = "${jwt.cleanup-interval:3600000}",
            initialDelayString = "${jwt.cleanup-initial-delay:3600000}")
    @Transactional
    public void cleanupExpiredTokens() {
        int deletedCount = deleteExpiredTokens(Instant.now());
        if (deletedCount > 0) {
            log.info("Deleted {} expired refresh tokens", deletedCount);
        }
    }

    @Transactional
    public int deleteExpiredTokens(Instant cutoff) {
        return refreshTokenRepository.deleteExpiredTokens(cutoff);
    }
}
