package com.shop.identity.internal.repository;

import com.shop.identity.internal.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
              from RefreshToken token
             where token.familyId = :familyId
             order by token.createdAt, token.jti
            """)
    List<RefreshToken> findFamilyForUpdate(@Param("familyId") String familyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
               set token.revokedAt = :revokedAt
             where token.familyId = :familyId
               and token.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
               set token.revokedAt = :revokedAt
             where token.user.id = :userId
               and token.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
               set token.revokedAt = :revokedAt
             where token.user.id in (
                   select user.id
                     from User user
                     join user.roles role
                    where role.code = :roleCode
             )
               and token.revokedAt is null
            """)
    int revokeAllForRole(@Param("roleCode") String roleCode, @Param("revokedAt") Instant revokedAt);

    boolean existsByFamilyIdAndRevokedAtIsNull(String familyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken token where token.expiresAt <= :cutoff")
    int deleteExpiredTokens(@Param("cutoff") Instant cutoff);
}
