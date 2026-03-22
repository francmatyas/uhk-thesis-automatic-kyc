package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.model.WorkerJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerJobRepository extends JpaRepository<WorkerJob, UUID>, JpaSpecificationExecutor<WorkerJob> {

    Optional<WorkerJob> findByIdempotencyKey(String idempotencyKey);

    @Query("""
              select j from WorkerJob j
              where j.status = 'RUNNING'
                and j.lastHeartbeatAt < :staleBefore
            """)
    List<WorkerJob> findStaleRunning(@Param("staleBefore") Instant staleBefore);

    @Query("""
              select j from WorkerJob j
              where j.status in ('QUEUED','CANCELLING','RUNNING')
                and j.id = :id
            """)
    Optional<WorkerJob> findActiveForCancel(@Param("id") UUID id);
}