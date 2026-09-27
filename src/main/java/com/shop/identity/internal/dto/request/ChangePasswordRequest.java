package com.shop.identity.internal.dto.request;

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
public class ChangePasswordRequest {

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = 8, max = 64, message = "INVALID_PASSWORD")
    String currentPassword;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = 8, max = 64, message = "INVALID_PASSWORD")
    String newPassword;
}
