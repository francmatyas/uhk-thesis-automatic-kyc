package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateWorkerJobRequest(
        String type,
        JsonNode payload,
        Integer priority,
        Long timeoutMs,
        String idempotencyKey
) {
}
