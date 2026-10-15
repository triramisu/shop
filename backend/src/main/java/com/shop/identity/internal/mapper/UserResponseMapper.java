package com.shop.identity.internal.mapper;

import com.shop.identity.internal.dto.response.UserResponse;
import com.shop.identity.internal.entity.Role;
import com.shop.identity.internal.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserResponseMapper {

    @Mapping(target = "roles", source = "roles")
    UserResponse toResponse(User user);

    default String mapRole(Role role) {
        return role.getCode();
    }
}
