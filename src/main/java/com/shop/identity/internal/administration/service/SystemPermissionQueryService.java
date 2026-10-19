package com.shop.identity.internal.administration.service;

import com.shop.identity.internal.administration.dto.response.PermissionResponse;
import com.shop.identity.internal.administration.mapper.SystemAdministrationMapper;
import com.shop.identity.internal.repository.PermissionRepository;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemPermissionQueryService {

    PermissionRepository permissionRepository;
    SystemAdministrationMapper mapper;

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll() {
        return permissionRepository.findAllByOrderByCodeAsc().stream()
                .map(mapper::toPermissionResponse)
                .toList();
    }
}
