package com.shop.identity.internal.repository;

import com.shop.identity.internal.entity.User;
import com.shop.identity.internal.entity.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    long countByRolesCode(String roleCode);

    boolean existsByRolesCode(String roleCode);

    Optional<User> findFirstByRolesCode(String roleCode);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select user from User user where user.id = :id")
    Optional<User> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select user from User user where lower(user.username) = lower(:username)")
    Optional<User> findDetailedByUsername(@Param("username") String username);

    @Query(value = """
                    select user
                      from User user
                     where (:status is null or user.status = :status)
                       and (:keyword is null
                            or lower(user.username) like :keyword escape '!'
                            or lower(user.email) like :keyword escape '!')
                    """, countQuery = """
                    select count(user)
                      from User user
                     where (:status is null or user.status = :status)
                       and (:keyword is null
                            or lower(user.username) like :keyword escape '!'
                            or lower(user.email) like :keyword escape '!')
                    """)
    Page<User> search(@Param("keyword") String keyword, @Param("status") UserStatus status, Pageable pageable);
}
