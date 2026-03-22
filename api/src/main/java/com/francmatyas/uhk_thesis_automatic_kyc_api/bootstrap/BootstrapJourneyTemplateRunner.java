package com.francmatyas.uhk_thesis_automatic_kyc_api.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplate;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplateStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.repository.JourneyTemplateRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(4)
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BootstrapJourneyTemplateRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapJourneyTemplateRunner.class);

    private static final String TEMPLATE_NAME = "Full KYC";

    private final TenantRepository tenantRepository;
    private final JourneyTemplateRepository journeyTemplateRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.bootstrap.test-tenant.slug:test-tenant}")
    private String testTenantSlug;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Tenant tenant = tenantRepository.findBySlug(testTenantSlug).orElse(null);
        if (tenant == null) {
            log.warn("Bootstrap journey template skipped: test tenant '{}' not found.", testTenantSlug);
            return;
        }

        if (journeyTemplateRepository.existsByNameAndTenantId(TEMPLATE_NAME, tenant.getId())) {
            log.info("Bootstrap journey template '{}' already exists for tenant '{}'.", TEMPLATE_NAME, testTenantSlug);
            return;
        }

        ObjectNode config = objectMapper.createObjectNode();
        ArrayNode checks = config.putArray("checks");
        checks.add("DOC_OCR");
        checks.add("LIVENESS");
        checks.add("SANCTIONS");
        checks.add("PEP");

        JourneyTemplate template = new JourneyTemplate();
        template.setTenantId(tenant.getId());
        template.setName(TEMPLATE_NAME);
        template.setDescription("Document check, liveness, sanctions screening and PEP check");
        template.setConfigJson(config);
        template.setStatus(JourneyTemplateStatus.ACTIVE);

        journeyTemplateRepository.save(template);
        log.info("Bootstrap journey template '{}' created for tenant '{}'.", TEMPLATE_NAME, testTenantSlug);
    }
}