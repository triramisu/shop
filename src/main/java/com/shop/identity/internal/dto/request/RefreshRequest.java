package com.shop.identity.internal.dto.request;

import static com.shop.identity.internal.constant.IdentityValidationConstants.MAX_TOKEN_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class RefreshRequest {

    @NotBlank(message = "INVALID_TOKEN")
    @Size(max = MAX_TOKEN_LENGTH, message = "INVALID_TOKEN")
    String token;
}
