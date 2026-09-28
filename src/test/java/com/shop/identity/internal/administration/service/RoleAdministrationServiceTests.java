package com.shop.identity.internal.administration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shop.identity.internal.administration.dto.request.UpdateRoleRequest;
import com.shop.identity.internal.administration.mapper.SystemAdministrationMapper;
import com.shop.identity.internal.entity.Role;
import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.repository.PermissionRepository;
import com.shop.identity.internal.repository.RefreshTokenRepository;
import com.shop.identity.internal.repository.RoleRepository;
import com.shop.identity.internal.repository.UserRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class RoleAdministrationServiceTests {

    private RoleRepository roleRepository;
    private PermissionRepository permissionRepository;
    private UserRepository userRepository;
    private RoleAdministrationService service;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        userRepository = mock(UserRepository.class);
        service = new RoleAdministrationService(
                roleRepository,
                permissionRepository,
                userRepository,
                mock(RefreshTokenRepository.class),
                mock(SystemAdministrationMapper.class));
    }

    @Test
    void mapsAStaleRoleUpdateToTheAdministrationConflictContract() {
        User admin = mock(User.class);
        Role role = Role.createCustom("OPS", "Operations", Set.of());
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .description("Updated operations")
                .permissionCodes(Set.of())
                .build();

        when(userRepository.findDetailedByUsername("root")).thenReturn(Optional.of(admin));
        when(admin.hasRole("ADMIN")).thenReturn(true);
        when(roleRepository.findDetailedByCode("OPS")).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(Set.of())).thenReturn(List.of());
        when(roleRepository.saveAndFlush(role))
                .thenThrow(new ObjectOptimisticLockingFailureException(Role.class, "OPS"));

        assertThatThrownBy(() -> service.update("root", "OPS", request))
                .isInstanceOfSatisfying(AppException.class, exception -> org.assertj.core.api.Assertions.assertThat(
                                exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMINISTRATION_CONFLICT));
    }
}
