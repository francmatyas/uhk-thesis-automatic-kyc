package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.model.WorkerJobStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record WorkerJobResponse(
        UUID id,
        String type,
        WorkerJobStatus status,
        int progressPct,
        String progressMsg,
        JsonNode result,
        JsonNode error,
        Integer attempt,
        Integer maxAttempts,
        String workerId,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt
) {
}
