package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto.JourneyTemplateListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto.JourneyTemplateTenantListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplate;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplateStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.repository.JourneyTemplateRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.DisplayFieldScanner;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JourneyTemplateService {

    private final JourneyTemplateRepository repository;

    public TableDTO getProviderJourneyTemplatesTable(int page, int size, String sortBy, String sortDir, UUID tenantId) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Sort.Direction dir = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortField = sortBy != null && !sortBy.isBlank() ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(safePage, safeSize, dir, sortField);

        Page<JourneyTemplate> result = tenantId != null
                ? repository.findAllByTenantId(tenantId, pageable)
                : repository.findAll(pageable);

        List<Column> columns = DisplayFieldScanner.getColumns(JourneyTemplateListDTO.class);
        List<JourneyTemplateListDTO> dtos = result.getContent().stream().map(this::toProviderListDto).toList();
        return TableDTO.builder()
                .columns(columns)
                .rows(DisplayFieldScanner.getDataMaps(dtos, columns))
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .build();
    }

    public TableDTO getTenantJourneyTemplatesTable(UUID tenantId, int page, int size, String sortBy, String sortDir) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Sort.Direction dir = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortField = sortBy != null && !sortBy.isBlank() ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(safePage, safeSize, dir, sortField);

        Page<JourneyTemplate> result = repository.findAllByTenantId(tenantId, pageable);

        List<Column> columns = DisplayFieldScanner.getColumns(JourneyTemplateTenantListDTO.class);
        List<JourneyTemplateTenantListDTO> dtos = result.getContent().stream().map(this::toTenantListDto).toList();
        return TableDTO.builder()
                .columns(columns)
                .rows(DisplayFieldScanner.getDataMaps(dtos, columns))
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .build();
    }

    public JourneyTemplate findByIdAndTenant(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("JourneyTemplate not found: " + id));
    }

    @Transactional
    public JourneyTemplate create(JourneyTemplate template) {
        return repository.save(template);
    }

    @Transactional
    public JourneyTemplate update(UUID id, UUID tenantId, JourneyTemplate patch) {
        JourneyTemplate existing = findByIdAndTenant(id, tenantId);
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getConfigJson() != null) existing.setConfigJson(patch.getConfigJson());
        if (patch.getStatus() != null) existing.setStatus(patch.getStatus());
        return repository.save(existing);
    }


    private JourneyTemplateListDTO toProviderListDto(JourneyTemplate t) {
        return JourneyTemplateListDTO.builder()
                .id(t.getId().toString())
                .name(t.getName())
                .status(t.getStatus() != null ? t.getStatus().name() : null)
                .tenantId(t.getTenantId() != null ? t.getTenantId().toString() : null)
                .createdAt(t.getCreatedAt())
                .build();
    }

    private JourneyTemplateTenantListDTO toTenantListDto(JourneyTemplate t) {
        return JourneyTemplateTenantListDTO.builder()
                .id(t.getId().toString())
                .name(t.getName())
                .status(t.getStatus() != null ? t.getStatus().name() : null)
                .createdAt(t.getCreatedAt())
                .build();
    }

    @Transactional
    public void archive(UUID id, UUID tenantId) {
        JourneyTemplate template = findByIdAndTenant(id, tenantId);
        template.setStatus(JourneyTemplateStatus.ARCHIVED);
        repository.save(template);
    }
}