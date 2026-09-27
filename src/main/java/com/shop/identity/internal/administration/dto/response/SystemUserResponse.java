package com.shop.identity.internal.administration.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
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
public class SystemUserResponse {
    UUID id;
    String username;
    String email;
    String firstName;
    String lastName;
    LocalDate dateOfBirth;
    String status;
    boolean emailVerified;
    Set<String> roles;
    Instant createdAt;
    Instant updatedAt;
}
