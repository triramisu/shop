package com.shop.identity.internal.entity;

import com.shop.identity.internal.constant.IdentityTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = IdentityTableNames.ROLES)
public class Role {

    @Id
    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 255)
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = IdentityTableNames.ROLE_PERMISSIONS,
            joinColumns = @JoinColumn(name = "role_code"),
            inverseJoinColumns = @JoinColumn(name = "permission_code"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {}

    private Role(String code, String description, boolean systemRole, Set<Permission> permissions) {
        this.code = code;
        this.description = description;
        this.systemRole = systemRole;
        this.permissions.addAll(permissions);
    }

    public static Role createCustom(String code, String description, Set<Permission> permissions) {
        return new Role(
                Objects.requireNonNull(code), description, false, new HashSet<>(Objects.requireNonNull(permissions)));
    }

    public void update(String description, Set<Permission> permissions) {
        if (systemRole) {
            throw new IllegalStateException("System roles cannot be modified");
        }
        this.description = description;
        this.permissions.clear();
        this.permissions.addAll(Objects.requireNonNull(permissions));
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }
}
