package com.shop.identity.internal.administration.service;

import com.shop.identity.internal.administration.dto.request.ReplaceUserRolesRequest;
import com.shop.identity.internal.administration.dto.request.UpdateUserStatusRequest;
import com.shop.identity.internal.administration.dto.request.UserSearchRequest;
import com.shop.identity.internal.administration.dto.response.SystemUserPageResponse;
import com.shop.identity.internal.administration.dto.response.SystemUserResponse;
import com.shop.identity.internal.administration.mapper.SystemAdministrationMapper;
import com.shop.identity.internal.constant.RoleCode;
import com.shop.identity.internal.entity.Permission;
import com.shop.identity.internal.entity.Role;
import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.entity.UserStatus;
import com.shop.identity.internal.repository.RefreshTokenRepository;
import com.shop.identity.internal.repository.RoleRepository;
import com.shop.identity.internal.repository.UserRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemUserAdministrationService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    RefreshTokenRepository refreshTokenRepository;
    SystemAdministrationMapper mapper;

    @Transactional(readOnly = true)
    public SystemUserPageResponse search(UserSearchRequest request) {
        String keyword = toLikePattern(request.getKeyword());
        PageRequest pageRequest = PageRequest.of(
                request.getPage(), request.getSize(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<User> users = userRepository.search(keyword, request.getStatus(), pageRequest);
        return mapper.toUserPageResponse(users);
    }

    @Transactional(readOnly = true)
    public SystemUserResponse getById(UUID userId) {
        return mapper.toUserResponse(getUser(userId));
    }

    @Transactional
    public SystemUserResponse updateStatus(String actorUsername, UUID userId, UpdateUserStatusRequest request) {
        User actor = getActor(actorUsername);
        User target = getUser(userId);
        boolean admin = actor.hasRole(RoleCode.ADMIN.name());

        if (target.hasRole(RoleCode.ADMIN.name())) {
            throw new AppException(ErrorCode.ADMIN_PROTECTED);
        }
        if (target.hasRole(RoleCode.STAFF.name()) && !admin) {
            throw new AppException(ErrorCode.PROTECTED_ROLE_ASSIGNMENT_FORBIDDEN);
        }
        if (actor.getId().equals(target.getId()) && request.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.SELF_LOCK_FORBIDDEN);
        }

        if (target.getStatus() != request.getStatus()) {
            target.changeStatus(request.getStatus());
            saveAndRevokeSessions(target);
        }
        return mapper.toUserResponse(target);
    }

    @Transactional
    public SystemUserResponse replaceRoles(String actorUsername, UUID userId, ReplaceUserRolesRequest request) {
        User actor = getActor(actorUsername);
        User target = getUser(userId);
        boolean admin = actor.hasRole(RoleCode.ADMIN.name());

        if (target.hasRole(RoleCode.ADMIN.name())) {
            throw new AppException(ErrorCode.ADMIN_PROTECTED);
        }

        Set<String> requestedCodes =
                request.getRoleCodes().stream().map(String::strip).collect(Collectors.toUnmodifiableSet());
        if (requestedCodes.contains(RoleCode.ADMIN.name())) {
            throw new AppException(ErrorCode.ADMIN_ASSIGNMENT_FORBIDDEN);
        }

        Set<Role> requestedRoles = new HashSet<>(roleRepository.findAllById(requestedCodes));
        if (requestedRoles.size() != requestedCodes.size()) {
            throw new AppException(ErrorCode.ROLE_NOT_FOUND);
        }

        validateAssignableRoles(actor, target, requestedRoles, admin);
        target.replaceRoles(requestedRoles);
        saveAndRevokeSessions(target);
        return mapper.toUserResponse(target);
    }

    private void validateAssignableRoles(User actor, User target, Set<Role> requestedRoles, boolean admin) {
        boolean targetIsStaff = target.hasRole(RoleCode.STAFF.name());
        boolean requestsStaff =
                requestedRoles.stream().anyMatch(role -> role.getCode().equals(RoleCode.STAFF.name()));
        if (!admin && (targetIsStaff || requestsStaff)) {
            throw new AppException(ErrorCode.PROTECTED_ROLE_ASSIGNMENT_FORBIDDEN);
        }
        if (admin) {
            return;
        }

        Set<String> actorPermissions = actor.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        boolean grantsUnknownPermission = requestedRoles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .anyMatch(permission -> !actorPermissions.contains(permission));
        if (grantsUnknownPermission) {
            throw new AppException(ErrorCode.PROTECTED_ROLE_ASSIGNMENT_FORBIDDEN);
        }
    }

    private User getActor(String username) {
        return userRepository
                .findDetailedByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private User getUser(UUID userId) {
        return userRepository.findDetailedById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void saveAndRevokeSessions(User user) {
        try {
            userRepository.saveAndFlush(user);
            refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.ADMINISTRATION_CONFLICT);
        }
    }

    private String toLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.strip()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
