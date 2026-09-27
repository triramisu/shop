package com.shop.identity.internal.dto.response;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationResponse {
    String accessToken;
    String refreshToken;

    @Builder.Default
    String tokenType = "Bearer";

    boolean authenticated;
    Instant accessTokenExpiresAt;
    Instant refreshTokenExpiresAt;
}
