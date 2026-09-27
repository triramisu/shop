package com.shop.identity.internal.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
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
public class RegisterUserRequest {

    @NotBlank(message = "USERNAME_REQUIRED")
    @Size(min = 3, max = 50, message = "USERNAME_INVALID")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "USERNAME_FORMAT_INVALID")
    String username;

    @NotBlank(message = "INVALID_EMAIL")
    @Email(message = "INVALID_EMAIL")
    @Size(max = 320, message = "INVALID_EMAIL")
    String email;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = 8, max = 64, message = "INVALID_PASSWORD")
    String password;

    @Size(max = 100, message = "FIRST_NAME_INVALID")
    String firstName;

    @Size(max = 100, message = "LAST_NAME_INVALID")
    String lastName;

    @Past(message = "INVALID_DOB")
    LocalDate dateOfBirth;
}
