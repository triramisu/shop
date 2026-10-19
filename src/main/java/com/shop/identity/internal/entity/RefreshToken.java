package com.shop.identity.internal.entity;

import com.shop.identity.internal.constant.IdentityTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;

@Entity
@Table(name = IdentityTableNames.REFRESH_SESSIONS)
public class RefreshToken {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String jti;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "family_id", nullable = false, updatable = false, length = 36)
    private String familyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_jti", length = 36)
    private String replacedByJti;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {}

    @Builder(access = AccessLevel.PRIVATE)
    private RefreshToken(
            String jti, User user, String familyId, String tokenHash, Instant expiresAt, Instant createdAt) {
        this.jti = jti;
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static RefreshToken issue(
            String jti, User user, String familyId, String tokenHash, Instant expiresAt, Instant createdAt) {
        return RefreshToken.builder()
                .jti(jti)
                .user(user)
                .familyId(familyId)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .build();
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void consume(Instant now, String replacementJti) {
        consumedAt = now;
        replacedByJti = replacementJti;
    }

    public String getJti() {
        return jti;
    }

    public User getUser() {
        return user;
    }

    public String getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }
}
