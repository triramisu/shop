package com.shop.identity.internal.administration.mapper;

import com.shop.identity.internal.administration.dto.response.PermissionResponse;
import com.shop.identity.internal.administration.dto.response.RoleResponse;
import com.shop.identity.internal.administration.dto.response.SystemUserPageResponse;
import com.shop.identity.internal.administration.dto.response.SystemUserResponse;
import com.shop.identity.internal.entity.Permission;
import com.shop.identity.internal.entity.Role;
import com.shop.identity.internal.entity.User;
import java.util.TreeSet;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class SystemAdministrationMapper {

    public SystemUserResponse toUserResponse(User user) {
        return SystemUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dateOfBirth(user.getDateOfBirth())
                .status(user.getStatus().name())
                .emailVerified(user.isEmailVerified())
                .roles(toRoleCodes(user))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public SystemUserPageResponse toUserPageResponse(Page<User> users) {
        return SystemUserPageResponse.builder()
                .content(users.getContent().stream().map(this::toUserResponse).toList())
                .page(users.getNumber())
                .size(users.getSize())
                .totalElements(users.getTotalElements())
                .totalPages(users.getTotalPages())
                .first(users.isFirst())
                .last(users.isLast())
                .build();
    }

    public RoleResponse toRoleResponse(Role role) {
        TreeSet<String> permissions = new TreeSet<>();
        role.getPermissions().stream().map(Permission::getCode).forEach(permissions::add);
        return RoleResponse.builder()
                .code(role.getCode())
                .description(role.getDescription())
                .systemRole(role.isSystemRole())
                .permissions(permissions)
                .build();
    }

    public PermissionResponse toPermissionResponse(Permission permission) {
        return PermissionResponse.builder()
                .code(permission.getCode())
                .description(permission.getDescription())
                .build();
    }

    private TreeSet<String> toRoleCodes(User user) {
        TreeSet<String> roles = new TreeSet<>();
        user.getRoles().stream().map(Role::getCode).forEach(roles::add);
        return roles;
    }
}
