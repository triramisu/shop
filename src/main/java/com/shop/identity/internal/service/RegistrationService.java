package com.shop.identity.internal.service;

import com.shop.identity.internal.constant.RoleCode;
import com.shop.identity.internal.dto.request.RegisterUserRequest;
import com.shop.identity.internal.dto.response.UserResponse;
import com.shop.identity.internal.entity.Role;
import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.mapper.UserResponseMapper;
import com.shop.identity.internal.repository.RoleRepository;
import com.shop.identity.internal.repository.UserRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
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
public class RegistrationService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    UserResponseMapper userResponseMapper;

    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        String username = request.getUsername().strip();
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Role defaultRole = roleRepository
                .findById(RoleCode.USER.name())
                .orElseThrow(() -> new AppException(ErrorCode.REQUIRED_ROLE_MISSING));

        User user = User.register(
                username,
                email,
                passwordEncoder.encode(request.getPassword()),
                normalizeOptional(request.getFirstName()),
                normalizeOptional(request.getLastName()),
                request.getDateOfBirth(),
                defaultRole);

        try {
            return userResponseMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.IDENTITY_CONFLICT);
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
