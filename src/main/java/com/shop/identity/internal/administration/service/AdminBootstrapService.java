package com.shop.identity.internal.administration.service;

import com.shop.identity.internal.administration.configuration.AdminBootstrapProperties;
import com.shop.identity.internal.constant.RoleCode;
import com.shop.identity.internal.entity.Role;
import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.entity.UserStatus;
import com.shop.identity.internal.repository.RoleRepository;
import com.shop.identity.internal.repository.UserRepository;
import com.shop.shared.error.ErrorMessageResolver;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminBootstrapService implements ApplicationRunner {

    AdminBootstrapProperties properties;
    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    ErrorMessageResolver messageResolver;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        validateProperties();
        long adminCount = userRepository.countByRolesCode(RoleCode.ADMIN.name());
        if (adminCount > 1) {
            throw configurationError("bootstrap.admin.multiple-accounts");
        }
        if (adminCount == 1) {
            verifyExistingAdmin();
            return;
        }
        if (!properties.isBootstrapEnabled()) {
            log.warn(messageResolver.resolve("bootstrap.admin.account-missing"));
            return;
        }
        createAdmin();
    }

    private void validateProperties() {
        if (!properties.isBootstrapEnabled()) {
            return;
        }
        if (properties.getUsername() == null || !properties.getUsername().matches("^[A-Za-z0-9._-]{3,50}$")) {
            throw configurationError("bootstrap.admin.username.invalid");
        }
        if (properties.getEmail() == null
                || properties.getEmail().length() > 320
                || !properties.getEmail().matches("^[^\\s@]+@[^\\s@]+$")) {
            throw configurationError("bootstrap.admin.email.invalid");
        }
        if (properties.getPassword() == null
                || properties.getPassword().length() < 12
                || properties.getPassword().length() > 64) {
            throw configurationError("bootstrap.admin.password.invalid");
        }
    }

    private void verifyExistingAdmin() {
        User admin = userRepository
                .findFirstByRolesCode(RoleCode.ADMIN.name())
                .orElseThrow(() -> configurationError("bootstrap.admin.count-inconsistent"));
        if (admin.getStatus() != UserStatus.ACTIVE) {
            throw configurationError("bootstrap.admin.must-remain-active");
        }
        if (properties.isBootstrapEnabled() && !admin.getUsername().equalsIgnoreCase(properties.getUsername())) {
            throw configurationError("bootstrap.admin.username-mismatch");
        }
    }

    private void createAdmin() {
        String username = properties.getUsername().strip();
        String email = properties.getEmail().strip().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByEmailIgnoreCase(email)) {
            throw configurationError("bootstrap.admin.identity-conflict");
        }

        Role adminRole = roleRepository
                .findById(RoleCode.ADMIN.name())
                .orElseThrow(() -> configurationError("bootstrap.admin.role-missing"));
        User admin = User.register(
                username, email, passwordEncoder.encode(properties.getPassword()), null, null, null, adminRole);
        userRepository.saveAndFlush(admin);
        log.info(messageResolver.resolve("bootstrap.admin.account-created", username));
    }

    private IllegalStateException configurationError(String messageKey) {
        return new IllegalStateException(messageResolver.resolve(messageKey));
    }
}
