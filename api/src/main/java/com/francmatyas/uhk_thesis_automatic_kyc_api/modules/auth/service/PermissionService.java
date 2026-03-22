package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto.PermissionDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto.PermissionListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Permission;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.PermissionRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.DisplayFieldScanner;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.SpecBuilder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionRepository permissionRepository;

    public TableDTO getAllPermissions(int page, int size, String sortBy, String sortDir, String q) {
        List<Column> columns = DisplayFieldScanner.getColumns(PermissionListDTO.class);
        Specification<Permission> spec = SpecBuilder.buildStringSearchSpec(q, columns);

        Sort.Direction dir = Sort.Direction.fromString(sortDir);
        Pageable pageReq = PageRequest.of(page, size, dir, sortBy);

        Page<Permission> pageEntity = permissionRepository.findAll(spec, pageReq);
        List<PermissionListDTO> dtos = pageEntity.stream().map(this::toListDto).toList();
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

    public PermissionDTO getPermissionById(String id) {
        Permission p = permissionRepository.findById(parseUuid(id))
                .orElseThrow(() -> new IllegalArgumentException("Permission not found"));
        return toDto(p);
    }

    @Transactional
    public PermissionDTO createPermission(PermissionDTO dto) {
        Permission p = Permission.builder()
                .resource(dto.getResource())
                .action(dto.getAction())
                .constraintJson(dto.getConstraintJson())
                .description(dto.getDescription())
                .build();

        Permission saved = permissionRepository.save(p);
        return toDto(saved);
    }

    @Transactional
    public PermissionDTO updatePermission(String id, PermissionDTO dto) {
        Permission p = permissionRepository.findById(parseUuid(id))
                .orElseThrow(() -> new IllegalArgumentException("Permission not found"));

        if (dto.getResource() != null) p.setResource(dto.getResource());
        if (dto.getAction() != null) p.setAction(dto.getAction());
        p.setDescription(dto.getDescription());
        p.setConstraintJson(dto.getConstraintJson());

        Permission saved = permissionRepository.save(p);
        return toDto(saved);
    }

    @Transactional
    public void deletePermission(String id) {
        Permission p = permissionRepository.findById(parseUuid(id))
                .orElseThrow(() -> new IllegalArgumentException("Permission not found"));
        permissionRepository.delete(p);
    }

    private PermissionDTO toDto(Permission p) {
        return PermissionDTO.builder()
                .id(p.getId() == null ? null : p.getId().toString())
                .label(p.getLabel())
                .description(p.getDescription())
                .resource(p.getResource())
                .action(p.getAction())
                .constraintJson(p.getConstraintJson())
                .build();
    }

    private PermissionListDTO toListDto(Permission p) {
        return PermissionListDTO.builder()
                .id(p.getId() == null ? null : p.getId().toString())
                .label(p.getLabel())
                .description(p.getDescription())
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
