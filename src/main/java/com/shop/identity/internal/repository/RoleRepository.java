package com.shop.identity.internal.repository;

import com.shop.identity.internal.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {}
