package com.shop.identity.internal.administration.service;

import static com.shop.identity.internal.administration.constant.SystemAdministrationAuthority.ROLE_MANAGE;

import com.shop.identity.internal.administration.dto.request.CreateRoleRequest;
import com.shop.identity.internal.administration.dto.request.UpdateRoleRequest;
import com.shop.identity.internal.administration.dto.response.RoleResponse;
import com.shop.identity.internal.administration.mapper.SystemAdministrationMapper;
import com.shop.identity.internal.constant.RoleCode;
import com.shop.identity.internal.entity.Permission;
import com.shop.identity.internal.entity.Role;
import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.repository.PermissionRepository;
import com.shop.identity.internal.repository.RefreshTokenRepository;
import com.shop.identity.internal.repository.RoleRepository;
import com.shop.identity.internal.repository.UserRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleAdministrationService {

    RoleRepository roleRepository;
    PermissionRepository permissionRepository;
    UserRepository userRepository;
    RefreshTokenRepository refreshTokenRepository;
    SystemAdministrationMapper mapper;

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAllDetailed().stream()
                .map(mapper::toRoleResponse)
                .toList();
    }

    @Transactional
    public RoleResponse create(String actorUsername, CreateRoleRequest request) {
        requireAdmin(actorUsername);
        String roleCode = request.getCode().strip();
        if (roleRepository.existsById(roleCode)) {
            throw new AppException(ErrorCode.ROLE_ALREADY_EXISTS);
        }

        Role role = Role.createCustom(
                roleCode,
                normalizeDescription(request.getDescription()),
                resolvePermissions(request.getPermissionCodes()));
        try {
            return mapper.toRoleResponse(roleRepository.saveAndFlush(role));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.ROLE_ALREADY_EXISTS);
        }
    }

    @Transactional
    public RoleResponse update(String actorUsername, String roleCode, UpdateRoleRequest request) {
        requireAdmin(actorUsername);
        Role role = getRole(roleCode);
        protectSystemRole(role);
        role.update(normalizeDescription(request.getDescription()), resolvePermissions(request.getPermissionCodes()));
        try {
            RoleResponse response = mapper.toRoleResponse(roleRepository.saveAndFlush(role));
            refreshTokenRepository.revokeAllForRole(role.getCode(), Instant.now());
            return response;
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.ADMINISTRATION_CONFLICT);
        }
    }

    @Transactional
    public void delete(String actorUsername, String roleCode) {
        requireAdmin(actorUsername);
        Role role = getRole(roleCode);
        protectSystemRole(role);
        if (userRepository.existsByRolesCode(role.getCode())) {
            throw new AppException(ErrorCode.ROLE_IN_USE);
        }
        try {
            roleRepository.delete(role);
            roleRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.ROLE_IN_USE);
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.ADMINISTRATION_CONFLICT);
        }
    }

    private void requireAdmin(String username) {
        User actor = userRepository
                .findDetailedByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if (!actor.hasRole(RoleCode.ADMIN.name())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private Role getRole(String roleCode) {
        return roleRepository
                .findDetailedByCode(roleCode.strip())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
    }

    private void protectSystemRole(Role role) {
        if (role.isSystemRole()) {
            throw new AppException(ErrorCode.SYSTEM_ROLE_PROTECTED);
        }
    }

    private Set<Permission> resolvePermissions(Set<String> permissionCodes) {
        Set<String> normalizedCodes =
                permissionCodes.stream().map(String::strip).collect(Collectors.toUnmodifiableSet());
        if (normalizedCodes.contains(ROLE_MANAGE)) {
            throw new AppException(ErrorCode.PROTECTED_PERMISSION_ASSIGNMENT_FORBIDDEN);
        }
        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(normalizedCodes));
        if (permissions.size() != normalizedCodes.size()) {
            throw new AppException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        return permissions;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
