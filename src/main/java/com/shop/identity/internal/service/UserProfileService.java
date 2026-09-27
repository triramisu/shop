package com.shop.identity.internal.service;

import com.shop.identity.internal.dto.request.ChangePasswordRequest;
import com.shop.identity.internal.dto.request.UpdateProfileRequest;
import com.shop.identity.internal.dto.response.UserResponse;
import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.entity.UserStatus;
import com.shop.identity.internal.mapper.UserResponseMapper;
import com.shop.identity.internal.repository.RefreshTokenRepository;
import com.shop.identity.internal.repository.UserRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileService {

    UserRepository userRepository;
    RefreshTokenRepository refreshTokenRepository;
    PasswordEncoder passwordEncoder;
    UserResponseMapper userResponseMapper;

    @Transactional(readOnly = true)
    public UserResponse getMyInfo(String username) {
        return userResponseMapper.toResponse(getActiveUser(username));
    }

    @Transactional
    public UserResponse updateProfile(String username, UpdateProfileRequest request) {
        User user = getActiveUser(username);
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        user.updateProfile(
                email,
                normalizeOptional(request.getFirstName()),
                normalizeOptional(request.getLastName()),
                request.getDateOfBirth());

        try {
            return userResponseMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.IDENTITY_CONFLICT);
        }
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = getActiveUser(username);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.PASSWORD_UNCHANGED);
        }

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.saveAndFlush(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    private User getActiveUser(String username) {
        User user = userRepository
                .findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_DISABLED);
        }
        return user;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
