package com.shop.identity.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class AuthenticationRequest {

    @NotBlank(message = "USERNAME_REQUIRED")
    String username;

    @NotBlank(message = "PASSWORD_REQUIRED")
    String password;

    @Size(max = 36, message = "INVALID_CAPTCHA")
    String captchaId;

    @Size(max = 8, message = "INVALID_CAPTCHA")
    @Pattern(regexp = "^[A-Za-z0-9]*$", message = "INVALID_CAPTCHA")
    String captchaAnswer;
}
