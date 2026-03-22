package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto.RoleDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto.RoleListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Permission;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Role;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RolePermission;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.PermissionRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.RolePermissionRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.RoleRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.DisplayFieldScanner;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.SpecBuilder;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final EntityManager entityManager;

    public TableDTO getAllRoles(int page, int size, String sortBy, String sortDir, String q) {
        List<Column> columns = DisplayFieldScanner.getColumns(RoleListDTO.class);
        Specification<Role> spec = SpecBuilder.buildStringSearchSpec(q, columns);

        Sort.Direction dir = Sort.Direction.fromString(sortDir);
        Pageable pageReq = PageRequest.of(page, size, dir, sortBy);

        Page<Role> pageEntity = roleRepository.findAll(spec, pageReq);
        List<RoleListDTO> dtos = pageEntity.stream().map(this::toListDto).toList();
        List<Map<String, Object>> rows = DisplayFieldScanner.getDataMaps(dtos, columns);

        return TableDTO.builder()
                .columns(columns)
                .rows(rows)
                .pageNumber(pageEntity.getNumber())
                .pageSize(pageEntity.getSize())
                .totalPages(pageEntity.getTotalPages())
                .totalElements(pageEntity.getTotalElements())
                .build();
    }

    public RoleDTO getRoleById(String id) {
        Role r = roleRepository.findById(parseUuid(id))
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        return toDto(r);
    }

    @Transactional
    public RoleDTO createRole(RoleDTO dto) {
        Role r = Role.builder()
                .name(dto.getName())
                .scope(dto.getScope())
                .description(dto.getDescription())
                .priority(dto.getPriority())
                .build();

        Role saved = roleRepository.save(r);
        if (dto.getPermissionIds() != null) {
            setRolePermissions(saved, dto.getPermissionIds());
        }
        return getRoleById(saved.getId().toString());
    }

    @Transactional
    public RoleDTO updateRole(String id, RoleDTO dto) {
        Role r = roleRepository.findById(parseUuid(id))
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (dto.getName() != null) r.setName(dto.getName());
        if (dto.getScope() != null) r.setScope(dto.getScope());
        r.setDescription(dto.getDescription());
        r.setPriority(dto.getPriority());

        Role saved = roleRepository.save(r);

        if (dto.getPermissionIds() != null) {
            setRolePermissions(saved, dto.getPermissionIds());
        }

        return getRoleById(saved.getId().toString());
    }

    @Transactional
    public void deleteRole(String id) {
        Role r = roleRepository.findById(parseUuid(id))
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        roleRepository.delete(r);
    }

    @Transactional
    public RoleDTO setRolePermissions(String roleId, List<String> permissionIds) {
        Role r = roleRepository.findById(parseUuid(roleId))
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        setRolePermissions(r, permissionIds);
        return getRoleById(roleId);
    }

    private void setRolePermissions(Role role, List<String> permissionIds) {
        rolePermissionRepository.deleteAllByRoleId(role.getId());

        if (permissionIds == null || permissionIds.isEmpty()) {
            entityManager.flush();
            entityManager.clear();
            return;
        }

        List<UUID> permUuids = permissionIds.stream()
                .map(this::parseUuid)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
        List<Permission> perms = permissionRepository.findAllById(permUuids);

        if (perms.size() != permUuids.size()) {
            throw new IllegalArgumentException("One or more permissions not found");
        }

        Map<UUID, Permission> permsById = perms.stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));

        List<RolePermission> rolePermissions = new ArrayList<>();
        for (UUID permissionId : permUuids) {
            Permission p = permsById.get(permissionId);
            if (p == null) {
                throw new IllegalArgumentException("One or more permissions not found");
            }

            RolePermission rp = new RolePermission();
            rp.setRole(role);
            rp.setPermission(p);
            rolePermissions.add(rp);
        }

        rolePermissionRepository.saveAll(rolePermissions);
        entityManager.flush();
        entityManager.clear();
    }

    private RoleDTO toDto(Role r) {
        List<String> permissionIds = r.getRolePermissions() == null
                ? List.of()
                : r.getRolePermissions().stream()
                .map(rp -> rp.getPermission() == null || rp.getPermission().getId() == null ? null : rp.getPermission().getId().toString())
                .filter(Objects::nonNull)
                .toList();

        return RoleDTO.builder()
                .id(r.getId() == null ? null : r.getId().toString())
                .name(r.getName())
                .scope(r.getScope())
                .slug(r.getSlug())
                .description(r.getDescription())
                .priority(r.getPriority())
                .permissionIds(permissionIds)
                .build();
    }

    private RoleListDTO toListDto(Role r) {
        return RoleListDTO.builder()
                .id(r.getId() == null ? null : r.getId().toString())
                .slug(r.getSlug())
                .scope(r.getScope() == null ? null : r.getScope().name())
                .priority(r.getPriority())
                .build();
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid id");
        }
    }
}
