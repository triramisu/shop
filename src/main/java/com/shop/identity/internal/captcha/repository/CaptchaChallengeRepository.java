package com.shop.identity.internal.captcha.repository;

import com.shop.identity.internal.captcha.entity.CaptchaChallenge;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaptchaChallengeRepository extends JpaRepository<CaptchaChallenge, String> {

    @Query(value = """
                    SELECT lock_name
                    FROM xac_thuc_khoa_captcha
                    WHERE lock_name = 'challenge-issuance'
                    FOR UPDATE
                    """, nativeQuery = true)
    String acquireIssuanceLock();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from CaptchaChallenge challenge where challenge.captchaId = :captchaId")
    Optional<CaptchaChallenge> findByCaptchaIdForUpdate(@Param("captchaId") String captchaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CaptchaChallenge challenge where challenge.principalHash = :principalHash")
    int deleteByPrincipalHash(@Param("principalHash") String principalHash);

    long countByExpiresAtAfter(Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CaptchaChallenge challenge where challenge.expiresAt <= :cutoff")
    int deleteExpiredChallenges(@Param("cutoff") Instant cutoff);
}
