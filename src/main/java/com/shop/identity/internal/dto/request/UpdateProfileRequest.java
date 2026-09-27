package com.shop.identity.internal.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
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
public class UpdateProfileRequest {

    @NotBlank(message = "INVALID_EMAIL")
    @Email(message = "INVALID_EMAIL")
    @Size(max = 320, message = "INVALID_EMAIL")
    String email;

    @Size(max = 100, message = "FIRST_NAME_INVALID")
    String firstName;

    @Size(max = 100, message = "LAST_NAME_INVALID")
    String lastName;

    @Past(message = "INVALID_DOB")
    LocalDate dateOfBirth;
}
