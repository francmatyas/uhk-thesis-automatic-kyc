package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum WebhookEventType {
    DOCUMENT_READY("document.ready"),
    DOCUMENT_DELETED("document.deleted");

    private final String eventName;

    WebhookEventType(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }

    public static Optional<WebhookEventType> fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);

        return Arrays.stream(values())
                .filter(v -> v.eventName.equalsIgnoreCase(normalized)
                        || v.name().equalsIgnoreCase(normalized.replace('.', '_').replace('-', '_')))
                .findFirst();
    }
}
