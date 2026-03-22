package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchScheduler.class);

    private final WebhookDispatcherService webhookDispatcherService;

    @Scheduled(fixedDelayString = "${app.webhooks.dispatcher.poll-ms:3000}")
    public void pollDueDeliveries() {
        try {
            webhookDispatcherService.processDueDeliveries();
        } catch (Exception ex) {
            log.error("Webhook dispatcher poll failed", ex);
        }
    }
}
