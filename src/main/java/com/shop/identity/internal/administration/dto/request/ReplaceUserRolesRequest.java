package com.shop.identity.internal.administration.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class ReplaceUserRolesRequest {

    @NotEmpty(message = "ROLE_SET_REQUIRED")
    @Size(max = 20, message = "ROLE_SET_INVALID")
    Set<
                    @NotBlank(message = "ROLE_CODE_REQUIRED")
                    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,49}$", message = "ROLE_CODE_INVALID") String>
            roleCodes;
}
