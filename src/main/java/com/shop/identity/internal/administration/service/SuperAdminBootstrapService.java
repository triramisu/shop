package com.shop.identity.internal.administration.service;

import com.shop.identity.internal.administration.configuration.SuperAdminBootstrapProperties;
import com.shop.identity.internal.constant.RoleCode;
import com.shop.identity.internal.entity.Role;
import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.entity.UserStatus;
import com.shop.identity.internal.repository.RoleRepository;
import com.shop.identity.internal.repository.UserRepository;
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
public class SuperAdminBootstrapService implements ApplicationRunner {

    SuperAdminBootstrapProperties properties;
    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        long superAdminCount = userRepository.countByRolesCode(RoleCode.SUPER_ADMIN.name());
        if (superAdminCount > 1) {
            throw new IllegalStateException("More than one SUPER_ADMIN account exists");
        }
        if (superAdminCount == 1) {
            verifyExistingSuperAdmin();
            return;
        }
        if (!properties.isBootstrapEnabled()) {
            log.warn("No SUPER_ADMIN account exists; enable the one-time bootstrap configuration to create it");
            return;
        }
        createSuperAdmin();
    }

    private void verifyExistingSuperAdmin() {
        User superAdmin = userRepository
                .findFirstByRolesCode(RoleCode.SUPER_ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN account count is inconsistent"));
        if (superAdmin.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("The SUPER_ADMIN account must remain active");
        }
        if (properties.isBootstrapEnabled() && !superAdmin.getUsername().equalsIgnoreCase(properties.getUsername())) {
            throw new IllegalStateException(
                    "Configured SUPER_ADMIN_USERNAME does not match the existing SUPER_ADMIN account");
        }
    }

    private void createSuperAdmin() {
        String username = properties.getUsername().strip();
        String email = properties.getEmail().strip().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("SUPER_ADMIN username or email is already assigned to another account");
        }

        Role superAdminRole = roleRepository
                .findById(RoleCode.SUPER_ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN system role is missing"));
        User superAdmin = User.register(
                username, email, passwordEncoder.encode(properties.getPassword()), null, null, null, superAdminRole);
        userRepository.saveAndFlush(superAdmin);
        log.info("Created the configured SUPER_ADMIN account {}", username);
    }
}
