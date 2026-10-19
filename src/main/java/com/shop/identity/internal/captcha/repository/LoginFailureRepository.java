package com.shop.identity.internal.captcha.repository;

import com.shop.identity.internal.captcha.entity.LoginFailure;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginFailureRepository extends JpaRepository<LoginFailure, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO xac_thuc_dang_nhap_that_bai (
                        principal_hash,
                        failure_count,
                        expires_at,
                        updated_at
                    )
                    VALUES (:principalHash, 1, :expiresAt, :now)
                    ON DUPLICATE KEY UPDATE
                        failure_count = CASE
                            WHEN expires_at <= :now THEN 1
                            ELSE failure_count + 1
                        END,
                        expires_at = CASE
                            WHEN expires_at <= :now THEN :expiresAt
                            ELSE expires_at
                        END,
                        updated_at = :now
                    """, nativeQuery = true)
    int recordFailure(
            @Param("principalHash") String principalHash,
            @Param("now") Instant now,
            @Param("expiresAt") Instant expiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LoginFailure failure where failure.expiresAt <= :cutoff")
    int deleteExpiredFailures(@Param("cutoff") Instant cutoff);
}
