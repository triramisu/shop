package com.shop.identity.internal.repository;

import com.shop.identity.internal.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, String> {}
