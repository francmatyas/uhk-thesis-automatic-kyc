package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto.ApiKeyListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto.WebhookEndpointListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKey;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKeyStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpoint;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpointSubscription;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEventType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpointStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.ApiKeyRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.WebhookEndpointRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.WebhookEndpointSubscriptionRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service.TenantAccessService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.DisplayFieldScanner;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IntegrationCredentialsService {

    private static final int MAX_PUBLIC_KEY_GENERATION_ATTEMPTS = 5;
    private static final int API_PUBLIC_KEY_RANDOM_BYTES = 24;
    private static final int API_SECRET_RANDOM_BYTES = 32;
    private static final int WEBHOOK_SECRET_RANDOM_BYTES = 32;

    private final ApiKeyRepository apiKeyRepository;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository;
    private final TenantRepository tenantRepository;
    private final TenantAccessService tenantAccessService;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public CreatedApiKey createApiKey(User currentUser, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name_required");
        }

        Tenant tenant = resolveTenantForUser(currentUser);

        String publicKey = generateUniquePublicKey();
        String rawSecret = "sk_" + randomToken(API_SECRET_RANDOM_BYTES);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(tenant);
        apiKey.setName(name.trim());
        apiKey.setPublicKey(publicKey);
        apiKey.setSecretHash(passwordEncoder.encode(rawSecret));
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        ApiKey saved = apiKeyRepository.save(apiKey);

        return new CreatedApiKey(
                saved.getId(),
                tenant.getId(),
                saved.getName(),
                saved.getPublicKey(),
                rawSecret,
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public CreatedWebhookEndpoint createWebhookEndpoint(User currentUser, String url, String secret, List<String> eventTypes) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url_required");
        }

        Tenant tenant = resolveTenantForUser(currentUser);
        String normalizedUrl = normalizeWebhookUrl(url);
        String resolvedSecret = resolveWebhookSecret(secret);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setTenant(tenant);
        endpoint.setUrl(normalizedUrl);
        endpoint.setSecret(resolvedSecret);
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        WebhookEndpoint saved = webhookEndpointRepository.save(endpoint);
        List<WebhookEventType> subscribedTypes = resolveWebhookEventTypes(eventTypes, true);
        replaceEndpointSubscriptions(saved, subscribedTypes);

        return new CreatedWebhookEndpoint(
                saved.getId(),
                tenant.getId(),
                saved.getUrl(),
                saved.getSecret(),
                saved.getStatus(),
                saved.getCreatedAt(),
                toEventTypeNames(subscribedTypes)
        );
    }

    @Transactional
    public Map<String, Object> updateApiKey(User currentUser, UUID apiKeyId, String name, String status) {
        Tenant tenant = resolveTenantForUser(currentUser);
        ApiKey apiKey = apiKeyRepository.findByIdAndTenantId(apiKeyId, tenant.getId())
                .orElseThrow(() -> new NoSuchElementException("api_key_not_found"));

        boolean hasChanges = false;
        if (name != null) {
            if (name.isBlank()) {
                throw new IllegalArgumentException("name_required");
            }
            apiKey.setName(name.trim());
            hasChanges = true;
        }

        if (status != null) {
            apiKey.setStatus(parseApiKeyStatus(status));
            hasChanges = true;
        }

        if (!hasChanges) {
            throw new IllegalArgumentException("update_required");
        }

        ApiKey saved = apiKeyRepository.save(apiKey);
        return toApiKeyDetailMap(saved, tenant.getId());
    }

    @Transactional
    public Map<String, Object> updateWebhookEndpoint(User currentUser, UUID webhookId, String status, List<String> eventTypes) {
        Tenant tenant = resolveTenantForUser(currentUser);
        WebhookEndpoint endpoint = webhookEndpointRepository.findByIdAndTenantId(webhookId, tenant.getId())
                .orElseThrow(() -> new NoSuchElementException("webhook_not_found"));

        boolean hasChanges = false;
        if (status != null) {
            endpoint.setStatus(parseWebhookStatus(status));
            hasChanges = true;
        }

        WebhookEndpoint saved = hasChanges ? webhookEndpointRepository.save(endpoint) : endpoint;
        if (eventTypes != null) {
            List<WebhookEventType> subscribedTypes = resolveWebhookEventTypes(eventTypes, false);
            replaceEndpointSubscriptions(saved, subscribedTypes);
            hasChanges = true;
        }
        if (!hasChanges) {
            throw new IllegalArgumentException("update_required");
        }
        return toWebhookDetailMap(saved, tenant.getId());
    }

    @Transactional
    public void deleteApiKey(User currentUser, UUID apiKeyId) {
        Tenant tenant = resolveTenantForUser(currentUser);
        ApiKey apiKey = apiKeyRepository.findByIdAndTenantId(apiKeyId, tenant.getId())
                .orElseThrow(() -> new NoSuchElementException("api_key_not_found"));

        apiKeyRepository.delete(apiKey);
    }

    @Transactional
    public void deleteWebhookEndpoint(User currentUser, UUID webhookId) {
        Tenant tenant = resolveTenantForUser(currentUser);
        WebhookEndpoint endpoint = webhookEndpointRepository.findByIdAndTenantId(webhookId, tenant.getId())
                .orElseThrow(() -> new NoSuchElementException("webhook_not_found"));

        webhookEndpointSubscriptionRepository.deleteByEndpointId(endpoint.getId());
        webhookEndpointRepository.delete(endpoint);
    }

    @Transactional(readOnly = true)
    public TableDTO getApiKeysTable(User currentUser, int page, int size, String sortBy, String sortDir, String q) {
        Tenant tenant = resolveTenantForUser(currentUser);
        List<ApiKey> apiKeys = apiKeyRepository.findByTenantId(tenant.getId());
        String query = q == null ? "" : q.trim().toLowerCase();

        List<ApiKeyListDTO> rows = apiKeys.stream()
                .filter(k -> matchesApiKeyQuery(k, query))
                .sorted(buildApiKeyComparator(sortBy, sortDir))
                .map(this::toApiKeyListDto)
                .toList();

        return toTable(rows, page, size, ApiKeyListDTO.class);
    }

    @Transactional(readOnly = true)
    public TableDTO getWebhookEndpointsTable(User currentUser, int page, int size, String sortBy, String sortDir, String q) {
        Tenant tenant = resolveTenantForUser(currentUser);
        List<WebhookEndpoint> endpoints = webhookEndpointRepository.findByTenantId(tenant.getId());
        String query = q == null ? "" : q.trim().toLowerCase();

        List<WebhookEndpointListDTO> rows = endpoints.stream()
                .filter(e -> matchesWebhookQuery(e, query))
                .sorted(buildWebhookComparator(sortBy, sortDir))
                .map(this::toWebhookListDto)
                .toList();

        return toTable(rows, page, size, WebhookEndpointListDTO.class);
    }

    @Transactional(readOnly = true)
    public WebhookEndpointOptions getWebhookEndpointOptions(User currentUser) {
        resolveTenantForUser(currentUser);
        List<String> statuses = Arrays.stream(WebhookEndpointStatus.values())
                .map(Enum::name)
                .toList();
        List<String> eventTypes = Arrays.stream(WebhookEventType.values())
                .map(WebhookEventType::eventName)
                .toList();
        return new WebhookEndpointOptions(statuses, eventTypes);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getApiKeyDetail(User currentUser, UUID apiKeyId) {
        Tenant tenant = resolveTenantForUser(currentUser);
        ApiKey apiKey = apiKeyRepository.findByIdAndTenantId(apiKeyId, tenant.getId())
                .orElseThrow(() -> new NoSuchElementException("api_key_not_found"));

        return toApiKeyDetailMap(apiKey, tenant.getId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWebhookEndpointDetail(User currentUser, UUID webhookId) {
        Tenant tenant = resolveTenantForUser(currentUser);
        WebhookEndpoint endpoint = webhookEndpointRepository.findByIdAndTenantId(webhookId, tenant.getId())
                .orElseThrow(() -> new NoSuchElementException("webhook_not_found"));

        return toWebhookDetailMap(endpoint, tenant.getId());
    }

    private Map<String, Object> toApiKeyDetailMap(ApiKey apiKey, UUID tenantId) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", apiKey.getId());
        body.put("tenantId", tenantId);
        body.put("name", apiKey.getName());
        body.put("publicKey", apiKey.getPublicKey());
        body.put("status", apiKey.getStatus());
        body.put("createdAt", apiKey.getCreatedAt());
        body.put("lastUsedAt", apiKey.getLastUsedAt());
        return body;
    }

    private Map<String, Object> toWebhookDetailMap(WebhookEndpoint endpoint, UUID tenantId) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", endpoint.getId());
        body.put("tenantId", tenantId);
        body.put("url", endpoint.getUrl());
        body.put("status", endpoint.getStatus());
        body.put("createdAt", endpoint.getCreatedAt());
        body.put("lastDeliveryAt", endpoint.getLastDeliveryAt());
        body.put("eventTypes", resolveEndpointEventTypeNames(endpoint.getId()));
        body.put("eventTypeOptions", allWebhookEventTypeNames());
        return body;
    }

    private Tenant resolveTenantForUser(User currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("unauthorized");
        }

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant_required");
        }

        if (!tenantAccessService.canAccessTenant(currentUser, tenantId)) {
            throw new AccessDeniedException("not_member_of_tenant");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("tenant_inactive");
        }

        return tenant;
    }

    private String normalizeWebhookUrl(String rawUrl) {
        String value = rawUrl.trim();
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("invalid_webhook_url");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("invalid_webhook_url");
            }
            assertNotPrivateAddress(host);
            return value;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid_webhook_url");
        }
    }

    private void assertNotPrivateAddress(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("invalid_webhook_url");
        }
        for (InetAddress addr : addresses) {
            if (isBlockedAddress(addr)) {
                throw new IllegalArgumentException("invalid_webhook_url");
            }
        }
    }

    private static boolean isBlockedAddress(InetAddress addr) {
        if (addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        // Blokovat IPv6-mapped IPv4 privátní adresy (např. ::ffff:127.0.0.1, ::ffff:10.0.0.1)
        if (addr instanceof Inet6Address inet6) {
            byte[] raw = inet6.getAddress();
            boolean isMapped = (raw[10] == (byte) 0xff && raw[11] == (byte) 0xff);
            if (isMapped) {
                try {
                    return isBlockedAddress(InetAddress.getByAddress(Arrays.copyOfRange(raw, 12, 16)));
                } catch (UnknownHostException ignored) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveWebhookSecret(String requestedSecret) {
        if (requestedSecret == null || requestedSecret.isBlank()) {
            return "whsec_" + randomToken(WEBHOOK_SECRET_RANDOM_BYTES);
        }
        return requestedSecret.trim();
    }

    private ApiKeyStatus parseApiKeyStatus(String status) {
        if (status.isBlank()) {
            throw new IllegalArgumentException("invalid_api_key_status");
        }

        try {
            return ApiKeyStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid_api_key_status");
        }
    }

    private WebhookEndpointStatus parseWebhookStatus(String status) {
        if (status.isBlank()) {
            throw new IllegalArgumentException("invalid_webhook_status");
        }

        try {
            return WebhookEndpointStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid_webhook_status");
        }
    }

    private List<WebhookEventType> resolveWebhookEventTypes(List<String> eventTypes, boolean defaultAllWhenEmpty) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            if (defaultAllWhenEmpty) {
                return List.of(WebhookEventType.values());
            }
            return List.of();
        }

        Set<WebhookEventType> unique = new LinkedHashSet<>();
        for (String raw : eventTypes) {
            WebhookEventType type = WebhookEventType.fromValue(raw)
                    .orElseThrow(() -> new IllegalArgumentException("invalid_webhook_event_type"));
            unique.add(type);
        }
        return List.copyOf(unique);
    }

    private void replaceEndpointSubscriptions(WebhookEndpoint endpoint, List<WebhookEventType> eventTypes) {
        webhookEndpointSubscriptionRepository.deleteByEndpointId(endpoint.getId());
        webhookEndpointSubscriptionRepository.flush();
        if (eventTypes == null || eventTypes.isEmpty()) {
            return;
        }

        List<WebhookEndpointSubscription> subscriptions = eventTypes.stream()
                .map(type -> WebhookEndpointSubscription.builder()
                        .endpoint(endpoint)
                        .eventType(type)
                        .enabled(true)
                        .build())
                .toList();
        webhookEndpointSubscriptionRepository.saveAll(subscriptions);
    }

    private List<String> resolveEndpointEventTypeNames(UUID endpointId) {
        List<WebhookEndpointSubscription> rows = webhookEndpointSubscriptionRepository.findByEndpointIdOrderByEventTypeAsc(endpointId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(WebhookEndpointSubscription::isEnabled)
                .map(WebhookEndpointSubscription::getEventType)
                .map(WebhookEventType::eventName)
                .collect(Collectors.toList());
    }

    private List<String> toEventTypeNames(List<WebhookEventType> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return List.of();
        }
        return eventTypes.stream()
                .map(WebhookEventType::eventName)
                .toList();
    }

    private List<String> allWebhookEventTypeNames() {
        return Arrays.stream(WebhookEventType.values())
                .map(WebhookEventType::eventName)
                .toList();
    }

    private String generateUniquePublicKey() {
        for (int i = 0; i < MAX_PUBLIC_KEY_GENERATION_ATTEMPTS; i++) {
            String candidate = "pk_" + randomToken(API_PUBLIC_KEY_RANDOM_BYTES);
            if (apiKeyRepository.findByPublicKey(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("api_key_generation_failed");
    }

    private ApiKeyListDTO toApiKeyListDto(ApiKey apiKey) {
        return ApiKeyListDTO.builder()
                .id(apiKey.getId().toString())
                .name(apiKey.getName())
                .publicKey(apiKey.getPublicKey())
                .status(apiKey.getStatus().name())
                .lastUsedAt(apiKey.getLastUsedAt())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }

    private WebhookEndpointListDTO toWebhookListDto(WebhookEndpoint endpoint) {
        return WebhookEndpointListDTO.builder()
                .id(endpoint.getId().toString())
                .url(endpoint.getUrl())
                .status(endpoint.getStatus().name())
                .lastDeliveryAt(endpoint.getLastDeliveryAt())
                .createdAt(endpoint.getCreatedAt())
                .build();
    }

    private boolean matchesApiKeyQuery(ApiKey apiKey, String query) {
        if (query.isBlank()) {
            return true;
        }
        return contains(apiKey.getName(), query)
                || contains(apiKey.getPublicKey(), query)
                || contains(apiKey.getStatus() != null ? apiKey.getStatus().name() : null, query);
    }

    private boolean matchesWebhookQuery(WebhookEndpoint endpoint, String query) {
        if (query.isBlank()) {
            return true;
        }
        return contains(endpoint.getUrl(), query)
                || contains(endpoint.getStatus() != null ? endpoint.getStatus().name() : null, query);
    }

    private Comparator<ApiKey> buildApiKeyComparator(String sortBy, String sortDir) {
        String normalizedSortBy = sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy.trim();
        Comparator<ApiKey> comparator = switch (normalizedSortBy) {
            case "id" -> Comparator.comparing(ApiKey::getId, Comparator.nullsLast(UUID::compareTo));
            case "name" -> Comparator.comparing(ApiKey::getName, String.CASE_INSENSITIVE_ORDER);
            case "publicKey" -> Comparator.comparing(ApiKey::getPublicKey, String.CASE_INSENSITIVE_ORDER);
            case "status" -> Comparator.comparing(k -> k.getStatus() != null ? k.getStatus().name() : "",
                    String.CASE_INSENSITIVE_ORDER);
            case "lastUsedAt" -> Comparator.comparing(ApiKey::getLastUsedAt,
                    Comparator.nullsLast(Instant::compareTo));
            default -> Comparator.comparing(ApiKey::getCreatedAt, Comparator.nullsLast(Instant::compareTo));
        };
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
    }

    private Comparator<WebhookEndpoint> buildWebhookComparator(String sortBy, String sortDir) {
        String normalizedSortBy = sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy.trim();
        Comparator<WebhookEndpoint> comparator = switch (normalizedSortBy) {
            case "id" -> Comparator.comparing(WebhookEndpoint::getId, Comparator.nullsLast(UUID::compareTo));
            case "url" -> Comparator.comparing(WebhookEndpoint::getUrl, String.CASE_INSENSITIVE_ORDER);
            case "status" -> Comparator.comparing(e -> e.getStatus() != null ? e.getStatus().name() : "",
                    String.CASE_INSENSITIVE_ORDER);
            case "lastDeliveryAt" -> Comparator.comparing(WebhookEndpoint::getLastDeliveryAt,
                    Comparator.nullsLast(Instant::compareTo));
            default -> Comparator.comparing(WebhookEndpoint::getCreatedAt, Comparator.nullsLast(Instant::compareTo));
        };
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private <T> TableDTO toTable(List<T> sortedRows, int page, int size, Class<T> dtoClass) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        int totalElements = sortedRows.size();
        int from = Math.min(safePage * safeSize, totalElements);
        int to = Math.min(from + safeSize, totalElements);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil(totalElements / (double) safeSize);

        List<Column> columns = DisplayFieldScanner.getColumns(dtoClass);
        List<T> pageRows = sortedRows.subList(from, to);

        return TableDTO.builder()
                .columns(columns)
                .rows(DisplayFieldScanner.getDataMaps(pageRows, columns))
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .build();
    }

    private String randomToken(int bytes) {
        byte[] raw = new byte[bytes];
        secureRandom.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public record CreatedApiKey(
            UUID id,
            UUID tenantId,
            String name,
            String publicKey,
            String secret,
            ApiKeyStatus status,
            Instant createdAt
    ) {
    }

    public record CreatedWebhookEndpoint(
            UUID id,
            UUID tenantId,
            String url,
            String secret,
            WebhookEndpointStatus status,
            Instant createdAt,
            List<String> eventTypes
    ) {
    }

    public record WebhookEndpointOptions(
            List<String> statuses,
            List<String> eventTypes
    ) {
    }
}
