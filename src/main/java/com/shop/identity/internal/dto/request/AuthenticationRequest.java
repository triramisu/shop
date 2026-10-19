package com.shop.identity.internal.dto.request;

import static com.shop.identity.internal.constant.IdentityValidationConstants.MAX_PASSWORD_LENGTH;
import static com.shop.identity.internal.constant.IdentityValidationConstants.MAX_USERNAME_LENGTH;
import static com.shop.identity.internal.constant.IdentityValidationConstants.MIN_PASSWORD_LENGTH;
import static com.shop.identity.internal.constant.IdentityValidationConstants.MIN_USERNAME_LENGTH;
import static com.shop.identity.internal.constant.IdentityValidationConstants.USERNAME_PATTERN;

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
    @Size(min = MIN_USERNAME_LENGTH, max = MAX_USERNAME_LENGTH, message = "USERNAME_INVALID")
    @Pattern(regexp = USERNAME_PATTERN, message = "USERNAME_FORMAT_INVALID")
    String username;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH, message = "INVALID_PASSWORD")
    String password;

    @Size(max = 36, message = "INVALID_CAPTCHA")
    String captchaId;

    @Size(max = 8, message = "INVALID_CAPTCHA")
    @Pattern(regexp = "^[A-Za-z0-9]*$", message = "INVALID_CAPTCHA")
    String captchaAnswer;
}
