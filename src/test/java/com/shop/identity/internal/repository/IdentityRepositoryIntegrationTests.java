package com.shop.identity.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.identity.internal.constant.RoleCode;
import com.shop.identity.internal.entity.Permission;
import com.shop.identity.internal.entity.RefreshToken;
import com.shop.identity.internal.entity.Role;
import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.entity.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IdentityRepositoryIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void resolvesUsersCaseInsensitivelyAndLoadsTheirAuthorizationGraph() {
        User user = saveUser("Repository.Owner", "Repository.Owner@Example.com");
        Role adminRole = roleRepository.findById(RoleCode.ADMIN.name()).orElseThrow();
        user.replaceRoles(Set.of(adminRole));
        userRepository.saveAndFlush(user);

        entityManager.clear();

        User detailed =
                userRepository.findDetailedByUsername("repository.owner").orElseThrow();
        assertThat(detailed.getId()).isEqualTo(user.getId());
        assertThat(detailed.getRoles()).extracting(Role::getCode).containsExactly(RoleCode.ADMIN.name());
        assertThat(detailed.getRoles().iterator().next().getPermissions())
                .extracting(Permission::getCode)
                .containsExactlyInAnyOrder(
                        "SYSTEM_USER_READ",
                        "SYSTEM_USER_STATUS_UPDATE",
                        "SYSTEM_USER_ROLE_ASSIGN",
                        "SYSTEM_ROLE_READ",
                        "SYSTEM_ROLE_MANAGE",
                        "SYSTEM_PERMISSION_READ");
        assertThat(entityManager
                        .getEntityManagerFactory()
                        .getPersistenceUnitUtil()
                        .isLoaded(detailed, "roles"))
                .isTrue();
        assertThat(entityManager
                        .getEntityManagerFactory()
                        .getPersistenceUnitUtil()
                        .isLoaded(detailed.getRoles().iterator().next(), "permissions"))
                .isTrue();
        assertThat(userRepository.findByEmailIgnoreCase("REPOSITORY.OWNER@EXAMPLE.COM"))
                .contains(detailed);
        assertThat(userRepository.existsByEmailIgnoreCaseAndIdNot("repository.owner@example.com", detailed.getId()))
                .isFalse();
    }

    @Test
    void treatsEscapedSearchWildcardsAsLiteralCharactersAndFiltersStatus() {
        saveUser("repository_literal", "repository-literal@example.com");
        User wildcardNeighbour = saveUser("repositoryXliteral", "repository-neighbour@example.com");
        wildcardNeighbour.changeStatus(UserStatus.LOCKED);
        userRepository.saveAndFlush(wildcardNeighbour);

        Page<User> literalUnderscore = userRepository.search("%repository!_%", null, PageRequest.of(0, 10));
        Page<User> locked = userRepository.search(null, UserStatus.LOCKED, PageRequest.of(0, 10));

        assertThat(literalUnderscore.getContent()).extracting(User::getUsername).containsExactly("repository_literal");
        assertThat(locked.getContent()).extracting(User::getUsername).containsExactly("repositoryXliteral");
    }

    @Test
    void locksTokenFamiliesAndBulkRevokesOnlyTheRequestedScope() {
        User user = saveUser("repository-token-owner", "repository-token-owner@example.com");
        Instant now = Instant.now();
        String firstFamily = UUID.randomUUID().toString();
        String secondFamily = UUID.randomUUID().toString();
        RefreshToken first = token(user, firstFamily, now.minusSeconds(2), now.plusSeconds(300));
        RefreshToken second = token(user, firstFamily, now.minusSeconds(1), now.plusSeconds(300));
        RefreshToken unrelated = token(user, secondFamily, now, now.plusSeconds(300));
        refreshTokenRepository.saveAllAndFlush(List.of(second, unrelated, first));

        List<RefreshToken> lockedFamily = refreshTokenRepository.findFamilyForUpdate(firstFamily);
        assertThat(lockedFamily).extracting(RefreshToken::getJti).containsExactly(first.getJti(), second.getJti());

        assertThat(refreshTokenRepository.revokeFamily(firstFamily, now)).isEqualTo(2);
        assertThat(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNull(firstFamily))
                .isFalse();
        assertThat(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNull(secondFamily))
                .isTrue();

        assertThat(refreshTokenRepository.revokeAllForRole(RoleCode.USER.name(), now.plusSeconds(1)))
                .isOne();
        assertThat(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNull(secondFamily))
                .isFalse();
    }

    private User saveUser(String username, String email) {
        Role userRole = roleRepository.findById(RoleCode.USER.name()).orElseThrow();
        return userRepository.saveAndFlush(
                User.register(username, email, "test-password-hash", null, null, null, userRole));
    }

    private RefreshToken token(User user, String familyId, Instant createdAt, Instant expiresAt) {
        return RefreshToken.issue(UUID.randomUUID().toString(), user, familyId, "a".repeat(64), expiresAt, createdAt);
    }
}
