package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedJsonNodeConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(
        name = "worker_jobs",
        indexes = {
                @Index(name = "idx_worker_jobs_status", columnList = "status"),
                @Index(name = "idx_worker_jobs_type_status", columnList = "type,status"),
                @Index(name = "idx_worker_jobs_workerId", columnList = "workerId"),
                @Index(name = "idx_worker_jobs_apiInstanceId", columnList = "apiInstanceId"),
                @Index(name = "idx_worker_jobs_startedAt", columnList = "startedAt"),
                @Index(name = "idx_worker_jobs_lastHeartbeatAt", columnList = "lastHeartbeatAt")
        },
        uniqueConstraints = {
                // (Volitelné) DB-level unique na jobId je implicitní jako PK; idempotence využívá částečný index přes migraci (níže)
        }
)
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@SQLDelete(sql = "UPDATE worker_jobs SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class WorkerJob extends BaseEntity {
    /**
     * Logický typ workeru / queue key, např. "simulator", "report"
     */
    @Column(nullable = false, length = 64)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkerJobStatus status = WorkerJobStatus.QUEUED;

    @Convert(converter = EncryptedJsonNodeConverter.class)
    @Column(columnDefinition = "text", nullable = false)
    private JsonNode payload;

    /**
     * Poslední známý průběh [0..100]
     */
    @Column(nullable = false)
    private int progressPct = 0;

    @Column(length = 512)
    private String progressMsg;

    /**
     * Výsledek při úspěchu. Šifrované při uložení.
     */
    @Convert(converter = EncryptedJsonNodeConverter.class)
    @Column(columnDefinition = "text")
    private JsonNode result;

    /**
     * Chyba {code,message,details} při selhání/zrušení. Šifrované při uložení.
     */
    @Convert(converter = EncryptedJsonNodeConverter.class)
    @Column(columnDefinition = "text")
    private JsonNode error;

    @Column(nullable = false)
    private int priority = 0;

    @Column(nullable = false)
    private int attempt = 1;

    @Column(nullable = false)
    private int maxAttempts = 3;

    @Column(nullable = false)
    private long timeoutMs = 3_600_000L;

    @Column(length = 64)
    private String apiInstanceId;

    @Column(length = 64)
    private String workerId;

    @Column(length = 128)
    private String idempotencyKey;

    /**
     * Časová razítka životního cyklu
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant queuedAt;

    private Instant startedAt;
    private Instant finishedAt;

    /**
     * Heartbeat od workeru pro detekci zastaralých RUNNING úloh
     */
    private Instant lastHeartbeatAt;

    /**
     * Pole pro kooperativní zrušení
     */
    private Instant cancelRequestedAt;
    private Instant cancelledAt;

    /**
     * Optimistické zamykání
     */
    @Version
    private long version;
}
