package com.shop.identity.internal.repository;

import com.shop.identity.internal.entity.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, String> {

    @EntityGraph(attributePaths = "permissions")
    @Query("select role from Role role where role.code = :code")
    Optional<Role> findDetailedByCode(@Param("code") String code);

    @EntityGraph(attributePaths = "permissions")
    @Query("select distinct role from Role role order by role.code")
    List<Role> findAllDetailed();
}
