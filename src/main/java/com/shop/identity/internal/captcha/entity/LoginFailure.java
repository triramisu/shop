package com.shop.identity.internal.captcha.entity;

import com.shop.identity.internal.constant.IdentityTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = IdentityTableNames.LOGIN_FAILURES)
public class LoginFailure {

    @Id
    @Column(name = "principal_hash", nullable = false, updatable = false, length = 64)
    private String principalHash;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LoginFailure() {}

    public boolean requiresCaptcha(int threshold, Instant now) {
        return expiresAt.isAfter(now) && failureCount >= threshold;
    }
}
