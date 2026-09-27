package com.shop.identity.internal.administration.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;
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
public class UpdateRoleRequest {

    @Size(max = 255, message = "ROLE_DESCRIPTION_INVALID")
    String description;

    @NotNull(message = "PERMISSION_SET_REQUIRED")
    @Size(max = 100, message = "PERMISSION_SET_INVALID")
    @Builder.Default
    Set<
                    @NotBlank(message = "PERMISSION_CODE_REQUIRED")
                    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,99}$", message = "PERMISSION_CODE_INVALID") String>
            permissionCodes = new HashSet<>();
}
