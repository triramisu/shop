package com.shop.identity.internal.service;

import java.time.Instant;
import lombok.Builder;

@Builder
record IssuedTokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        String refreshJti,
        String familyId,
        Instant refreshTokenExpiresAt,
        Instant issuedAt) {}
