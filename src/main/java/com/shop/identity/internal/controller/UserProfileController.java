package com.shop.identity.internal.controller;

import com.shop.identity.internal.constant.IdentityApiPaths;
import com.shop.identity.internal.dto.request.ChangePasswordRequest;
import com.shop.identity.internal.dto.request.UpdateProfileRequest;
import com.shop.identity.internal.dto.response.UserResponse;
import com.shop.identity.internal.service.UserProfileService;
import com.shop.shared.web.ApiResponse;
import com.shop.shared.web.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(IdentityApiPaths.AUTH_BASE)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH_SCHEME)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileController {

    UserProfileService userProfileService;

    @GetMapping(IdentityApiPaths.MY_INFO_SEGMENT)
    ApiResponse<UserResponse> getMyInfo(Principal principal) {
        UserResponse result = userProfileService.getMyInfo(principal.getName());
        return ApiResponse.<UserResponse>builder().result(result).build();
    }

    @PutMapping(IdentityApiPaths.MY_INFO_SEGMENT)
    ApiResponse<UserResponse> updateProfile(Principal principal, @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse result = userProfileService.updateProfile(principal.getName(), request);
        return ApiResponse.<UserResponse>builder().result(result).build();
    }

    @PutMapping(IdentityApiPaths.MY_INFO_SEGMENT + IdentityApiPaths.PASSWORD_SEGMENT)
    ApiResponse<Void> changePassword(Principal principal, @Valid @RequestBody ChangePasswordRequest request) {
        userProfileService.changePassword(principal.getName(), request);
        return ApiResponse.<Void>builder().build();
    }
}
