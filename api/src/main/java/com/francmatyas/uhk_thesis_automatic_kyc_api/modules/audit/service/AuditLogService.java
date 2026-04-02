package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.dto.AuditLogDetailDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.dto.AuditLogListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.dto.AuditLogProviderListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditActorType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditLog;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.repository.AuditLogRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.DisplayFieldScanner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository repo;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Transactional
    public AuditLog log(AuditLogCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        AuditActorType actorType = resolveActorType(command);
        validateActorReferences(actorType, command.actorUserId(), command.actorApiKeyId());

        AuditLog audit = AuditLog.builder()
                .tenantId(command.tenantId())
                .actorUserId(command.actorUserId())
                .actorType(actorType)
                .actorApiKeyId(command.actorApiKeyId())
                .entityType(requireNonBlank(command.entityType(), "entityType", 64))
                .entityId(requireNonBlank(command.entityId(), "entityId"))
                .action(requireNonBlank(command.action(), "action", 64))
                .oldValue(toJsonNode(command.oldValue()))
                .newValue(toJsonNode(command.newValue()))
                .metadata(normalizeMetadata(command.metadata()))
                .ipAddress(normalizeIpAddress(command.ipAddress()))
                .userAgent(trimToNull(command.userAgent()))
                .correlationId(command.correlationId())
                .requestId(trimToNull(command.requestId(), 128))
                .result(command.result() != null ? command.result() : AuditResult.SUCCESS)
                .errorCode(trimToNull(command.errorCode(), 64))
                .build();

        return repo.save(audit);
    }

    @Transactional
    public AuditLog logUserAction(UUID actorUserId,
                                  UUID tenantId,
                                  String entityType,
                                  String entityId,
                                  String action,
                                  Object oldValue,
                                  Object newValue,
                                  Map<String, Object> metadata,
                                  String ipAddress,
                                  String userAgent,
                                  UUID correlationId,
                                  String requestId) {
        return log(new AuditLogCommand(
                tenantId,
                actorUserId,
                AuditActorType.USER,
                null,
                entityType,
                entityId,
                action,
                oldValue,
                newValue,
                metadata,
                ipAddress,
                userAgent,
                correlationId,
                requestId,
                AuditResult.SUCCESS,
                null
        ));
    }

    public TableDTO getTenantAuditLogsTable(UUID tenantId, int page, int size, String entityType, String action, UUID actorUserId) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        Page<AuditLog> result = repo.findByTenantFiltered(
                tenantId,
                blankToNull(entityType),
                blankToNull(action),
                actorUserId,
                PageRequest.of(safePage, safeSize));

        return toTableDTO(result, safePage, safeSize);
    }

    public TableDTO getProviderAuditLogsTable(UUID tenantId, int page, int size, String entityType, String action, UUID actorUserId) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        Page<AuditLog> result = repo.findAllFiltered(
                tenantId,
                blankToNull(entityType),
                blankToNull(action),
                actorUserId,
                PageRequest.of(safePage, safeSize));

        return toProviderTableDTO(result, safePage, safeSize);
    }

    public AuditLogDetailDTO getTenantAuditLogDetail(UUID tenantId, UUID id) {
        AuditLog log = repo.findById(id).orElseThrow(() -> new NoSuchElementException("audit_log_not_found"));
        if (!tenantId.equals(log.getTenantId())) {
            throw new NoSuchElementException("audit_log_not_found");
        }
        return toDetailDTO(log);
    }

    public AuditLogDetailDTO getProviderAuditLogDetail(UUID id) {
        AuditLog log = repo.findById(id).orElseThrow(() -> new NoSuchElementException("audit_log_not_found"));
        return toDetailDTO(log);
    }

    private TableDTO toTableDTO(Page<AuditLog> page, int safePage, int safeSize) {
        List<Column> columns = DisplayFieldScanner.getColumns(AuditLogListDTO.class);
        List<AuditLogListDTO> dtos = page.getContent().stream().map(this::toListDTO).toList();

        return TableDTO.builder()
                .columns(columns)
                .rows(DisplayFieldScanner.getDataMaps(dtos, columns))
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }

    private TableDTO toProviderTableDTO(Page<AuditLog> page, int safePage, int safeSize) {
        List<Column> columns = DisplayFieldScanner.getColumns(AuditLogProviderListDTO.class);
        List<AuditLogProviderListDTO> dtos = page.getContent().stream().map(this::toProviderListDTO).toList();

        return TableDTO.builder()
                .columns(columns)
                .rows(DisplayFieldScanner.getDataMaps(dtos, columns))
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }

    private AuditLogListDTO toListDTO(AuditLog a) {
        String actorUserId = null;
        String actorUserName = null;
        if (a.getActorType() == AuditActorType.USER && a.getActorUserId() != null) {
            actorUserId = a.getActorUserId().toString();
            actorUserName = userRepository.findById(a.getActorUserId())
                    .map(User::getFullName).orElse(actorUserId);
        }
        return AuditLogListDTO.builder()
                .id(a.getId().toString())
                .action(a.getAction())
                .createdAt(a.getCreatedAt())
                .actorType(a.getActorType().name())
                .entityType(a.getEntityType())
                .result(a.getResult().name())
                .actorUserId(actorUserId)
                .actorUserName(actorUserName)
                .build();
    }

    private AuditLogProviderListDTO toProviderListDTO(AuditLog a) {
        String actorUserId = null;
        String actorUserName = null;
        if (a.getActorType() == AuditActorType.USER && a.getActorUserId() != null) {
            actorUserId = a.getActorUserId().toString();
            actorUserName = userRepository.findById(a.getActorUserId())
                    .map(User::getFullName).orElse(actorUserId);
        }
        return AuditLogProviderListDTO.builder()
                .id(a.getId().toString())
                .action(a.getAction())
                .createdAt(a.getCreatedAt())
                .actorType(a.getActorType().name())
                .entityType(a.getEntityType())
                .result(a.getResult().name())
                .actorUserId(actorUserId)
                .actorUserName(actorUserName)
                .build();
    }

    private AuditLogDetailDTO toDetailDTO(AuditLog a) {
        return AuditLogDetailDTO.builder()
                .id(a.getId())
                .createdAt(a.getCreatedAt())
                .tenantId(a.getTenantId())
                //.actorUserId(a.getActorUserId())
                .actorType(a.getActorType().name())
                //.actorApiKeyId(a.getActorApiKeyId())
                .entityType(a.getEntityType())
                //.entityId(a.getEntityId())
                .action(a.getAction())
                //.oldValue(a.getOldValue())
                //.newValue(a.getNewValue())
                //.metadata(a.getMetadata())
                //.ipAddress(a.getIpAddress())
                //.userAgent(a.getUserAgent())
                //.correlationId(a.getCorrelationId())
                //.requestId(a.getRequestId())
                .result(a.getResult().name())
                .errorCode(a.getErrorCode())
                .build();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private AuditActorType resolveActorType(AuditLogCommand command) {
        if (command.actorType() != null) {
            return command.actorType();
        }
        if (command.actorUserId() != null) {
            return AuditActorType.USER;
        }
        if (command.actorApiKeyId() != null) {
            return AuditActorType.API_KEY;
        }
        return AuditActorType.SYSTEM;
    }

    private void validateActorReferences(AuditActorType actorType, UUID actorUserId, UUID actorApiKeyId) {
        boolean hasUser = actorUserId != null;
        boolean hasApiKey = actorApiKeyId != null;

        switch (actorType) {
            case USER -> {
                if (!hasUser || hasApiKey) {
                    throw new IllegalArgumentException("USER actor requires actorUserId and forbids actorApiKeyId");
                }
            }
            case API_KEY -> {
                if (!hasApiKey || hasUser) {
                    throw new IllegalArgumentException("API_KEY actor requires actorApiKeyId and forbids actorUserId");
                }
            }
            case SYSTEM, SERVICE -> {
                if (hasUser || hasApiKey) {
                    throw new IllegalArgumentException(actorType + " actor forbids actor references");
                }
            }
        }
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        return objectMapper.valueToTree(value);
    }

    private Map<String, Object> normalizeMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(metadata);
    }

    private String requireNonBlank(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireNonBlank(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String trimToNull(String value, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String normalizeIpAddress(String value) {
        String ip = trimToNull(value);
        if (ip == null) {
            return null;
        }

        int commaIdx = ip.indexOf(',');
        if (commaIdx >= 0) {
            ip = trimToNull(ip.substring(0, commaIdx));
        }

        if (ip != null && ip.regionMatches(true, 0, "for=", 0, 4)) {
            ip = trimToNull(ip.substring(4));
        }

        if (ip != null && ip.length() >= 2 && ip.startsWith("\"") && ip.endsWith("\"")) {
            ip = trimToNull(ip.substring(1, ip.length() - 1));
        }

        if (ip != null && ip.startsWith("[") && ip.endsWith("]") && ip.length() > 2) {
            ip = trimToNull(ip.substring(1, ip.length() - 1));
        }

        if (ip != null && ip.contains(".") && ip.indexOf(':') == ip.lastIndexOf(':')) {
            int colon = ip.indexOf(':');
            if (colon > 0) {
                ip = trimToNull(ip.substring(0, colon));
            }
        }

        if (ip == null) {
            return null;
        }

        try {
            return InetAddress.getByName(ip).getHostAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}
