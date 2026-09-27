package com.shop.identity.internal.administration.controller;

import static com.shop.identity.internal.administration.constant.SystemAdministrationApiPaths.BASE;
import static com.shop.identity.internal.administration.constant.SystemAdministrationApiPaths.PERMISSIONS;
import static com.shop.identity.internal.administration.constant.SystemAdministrationApiPaths.ROLES;
import static com.shop.identity.internal.administration.constant.SystemAdministrationApiPaths.ROLE_BY_CODE;
import static com.shop.identity.internal.administration.constant.SystemAdministrationApiPaths.USERS;
import static com.shop.identity.internal.administration.constant.SystemAdministrationApiPaths.USER_BY_ID;
import static com.shop.identity.internal.administration.constant.SystemAdministrationApiPaths.USER_ROLES;
import static com.shop.identity.internal.administration.constant.SystemAdministrationApiPaths.USER_STATUS;
import static com.shop.identity.internal.administration.constant.SystemAdministrationAuthority.PERMISSION_READ;
import static com.shop.identity.internal.administration.constant.SystemAdministrationAuthority.ROLE_MANAGE;
import static com.shop.identity.internal.administration.constant.SystemAdministrationAuthority.ROLE_READ;
import static com.shop.identity.internal.administration.constant.SystemAdministrationAuthority.USER_READ;
import static com.shop.identity.internal.administration.constant.SystemAdministrationAuthority.USER_ROLE_ASSIGN;
import static com.shop.identity.internal.administration.constant.SystemAdministrationAuthority.USER_STATUS_UPDATE;

import com.shop.identity.internal.administration.dto.request.CreateRoleRequest;
import com.shop.identity.internal.administration.dto.request.ReplaceUserRolesRequest;
import com.shop.identity.internal.administration.dto.request.UpdateRoleRequest;
import com.shop.identity.internal.administration.dto.request.UpdateUserStatusRequest;
import com.shop.identity.internal.administration.dto.request.UserSearchRequest;
import com.shop.identity.internal.administration.dto.response.PermissionResponse;
import com.shop.identity.internal.administration.dto.response.RoleResponse;
import com.shop.identity.internal.administration.dto.response.SystemUserPageResponse;
import com.shop.identity.internal.administration.dto.response.SystemUserResponse;
import com.shop.identity.internal.administration.service.RoleAdministrationService;
import com.shop.identity.internal.administration.service.SystemPermissionQueryService;
import com.shop.identity.internal.administration.service.SystemUserAdministrationService;
import com.shop.shared.web.ApiResponse;
import com.shop.shared.web.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(BASE)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH_SCHEME)
@Tag(name = "System administration", description = "Manage users, roles, and system permissions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemAdministrationController {

    SystemUserAdministrationService userAdministrationService;
    RoleAdministrationService roleAdministrationService;
    SystemPermissionQueryService permissionQueryService;

    @GetMapping(USERS)
    @PreAuthorize("hasAuthority('" + USER_READ + "')")
    @Operation(summary = "Search and page system users")
    ApiResponse<SystemUserPageResponse> searchUsers(@Valid @ModelAttribute UserSearchRequest request) {
        return ApiResponse.<SystemUserPageResponse>builder()
                .result(userAdministrationService.search(request))
                .build();
    }

    @GetMapping(USER_BY_ID)
    @PreAuthorize("hasAuthority('" + USER_READ + "')")
    @Operation(summary = "Get a system user")
    ApiResponse<SystemUserResponse> getUser(@PathVariable UUID userId) {
        return ApiResponse.<SystemUserResponse>builder()
                .result(userAdministrationService.getById(userId))
                .build();
    }

    @PatchMapping(USER_STATUS)
    @PreAuthorize("hasAuthority('" + USER_STATUS_UPDATE + "')")
    @Operation(summary = "Change user account status and revoke active sessions")
    ApiResponse<SystemUserResponse> updateUserStatus(
            Principal principal, @PathVariable UUID userId, @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.<SystemUserResponse>builder()
                .result(userAdministrationService.updateStatus(principal.getName(), userId, request))
                .build();
    }

    @PutMapping(USER_ROLES)
    @PreAuthorize("hasAuthority('" + USER_ROLE_ASSIGN + "')")
    @Operation(summary = "Replace user roles and revoke active sessions")
    ApiResponse<SystemUserResponse> replaceUserRoles(
            Principal principal, @PathVariable UUID userId, @Valid @RequestBody ReplaceUserRolesRequest request) {
        return ApiResponse.<SystemUserResponse>builder()
                .result(userAdministrationService.replaceRoles(principal.getName(), userId, request))
                .build();
    }

    @GetMapping(ROLES)
    @PreAuthorize("hasAuthority('" + ROLE_READ + "')")
    @Operation(summary = "List roles and assigned permissions")
    ApiResponse<List<RoleResponse>> getRoles() {
        return ApiResponse.<List<RoleResponse>>builder()
                .result(roleAdministrationService.findAll())
                .build();
    }

    @PostMapping(ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + ROLE_MANAGE + "')")
    @Operation(summary = "Create a custom role")
    ApiResponse<RoleResponse> createRole(Principal principal, @Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.<RoleResponse>builder()
                .result(roleAdministrationService.create(principal.getName(), request))
                .build();
    }

    @PutMapping(ROLE_BY_CODE)
    @PreAuthorize("hasAuthority('" + ROLE_MANAGE + "')")
    @Operation(summary = "Update a custom role")
    ApiResponse<RoleResponse> updateRole(
            Principal principal, @PathVariable String roleCode, @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.<RoleResponse>builder()
                .result(roleAdministrationService.update(principal.getName(), roleCode, request))
                .build();
    }

    @DeleteMapping(ROLE_BY_CODE)
    @PreAuthorize("hasAuthority('" + ROLE_MANAGE + "')")
    @Operation(summary = "Delete an unused custom role")
    ApiResponse<Void> deleteRole(Principal principal, @PathVariable String roleCode) {
        roleAdministrationService.delete(principal.getName(), roleCode);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping(PERMISSIONS)
    @PreAuthorize("hasAuthority('" + PERMISSION_READ + "')")
    @Operation(summary = "List code-owned system permissions")
    ApiResponse<List<PermissionResponse>> getPermissions() {
        return ApiResponse.<List<PermissionResponse>>builder()
                .result(permissionQueryService.findAll())
                .build();
    }
}
