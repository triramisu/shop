package com.shop.identity.internal.captcha.entity;

import com.shop.identity.internal.constant.IdentityTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;

@Entity
@Table(name = IdentityTableNames.CAPTCHA_CHALLENGES)
public class CaptchaChallenge {

    @Id
    @Column(name = "captcha_id", nullable = false, updatable = false, length = 36)
    private String captchaId;

    @Column(name = "principal_hash", nullable = false, updatable = false, length = 64)
    private String principalHash;

    @Column(name = "answer_hash", nullable = false, updatable = false, length = 64)
    private String answerHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CaptchaChallenge() {}

    @Builder(access = AccessLevel.PRIVATE)
    private CaptchaChallenge(
            String captchaId, String principalHash, String answerHash, Instant expiresAt, Instant createdAt) {
        this.captchaId = captchaId;
        this.principalHash = principalHash;
        this.answerHash = answerHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static CaptchaChallenge issue(
            String captchaId, String principalHash, String answerHash, Instant expiresAt, Instant createdAt) {
        return CaptchaChallenge.builder()
                .captchaId(captchaId)
                .principalHash(principalHash)
                .answerHash(answerHash)
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .build();
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public String getPrincipalHash() {
        return principalHash;
    }

    public String getAnswerHash() {
        return answerHash;
    }
}
