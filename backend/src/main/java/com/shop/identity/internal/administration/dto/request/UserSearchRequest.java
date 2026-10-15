package com.shop.identity.internal.administration.dto.request;

import com.shop.identity.internal.entity.UserStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class UserSearchRequest {

    @Size(max = 100, message = "SEARCH_KEYWORD_INVALID")
    String keyword;

    UserStatus status;

    @Min(value = 0, message = "PAGE_NUMBER_INVALID")
    @Builder.Default
    int page = 0;

    @Min(value = 1, message = "PAGE_SIZE_INVALID")
    @Max(value = 100, message = "PAGE_SIZE_INVALID")
    @Builder.Default
    int size = 20;
}
