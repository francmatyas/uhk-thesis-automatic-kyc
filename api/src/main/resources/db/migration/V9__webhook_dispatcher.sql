create table if not exists webhook_delivery_jobs
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone not null,
    updated_at       timestamp(6) with time zone not null,
    tenant_id        uuid                           not null,
    endpoint_id      uuid                           not null,
    event_type       varchar(128)                   not null,
    event_payload    jsonb                          not null,
    status           varchar(32)                    not null,
    attempt_count    integer                        not null default 0,
    max_attempts     integer                        not null default 6,
    next_attempt_at  timestamp(6) with time zone,
    last_attempt_at  timestamp(6) with time zone,
    last_status_code integer,
    last_error       text,
    correlation_id   uuid,
    request_id       varchar(128),
    version          bigint                         not null default 0
);

create index if not exists ix_webhook_delivery_jobs_status_next
    on webhook_delivery_jobs (status, next_attempt_at);
create index if not exists ix_webhook_delivery_jobs_endpoint
    on webhook_delivery_jobs (endpoint_id);
create index if not exists ix_webhook_delivery_jobs_tenant_created
    on webhook_delivery_jobs (tenant_id, created_at desc);

create table if not exists webhook_delivery_attempts
(
    id              uuid primary key,
    delivery_job_id uuid                           not null,
    attempt_no      integer                        not null,
    requested_at    timestamp(6) with time zone   not null,
    completed_at    timestamp(6) with time zone   not null,
    duration_ms     bigint,
    status_code     integer,
    success         boolean                        not null,
    error_message   text,
    response_body   text
);

create index if not exists ix_webhook_delivery_attempts_job
    on webhook_delivery_attempts (delivery_job_id, requested_at);
create unique index if not exists uq_webhook_delivery_attempts_job_attempt
    on webhook_delivery_attempts (delivery_job_id, attempt_no);

do
$$
    begin
        if not exists (select 1 from pg_constraint where conname = 'ck_webhook_delivery_jobs_status') then
            alter table webhook_delivery_jobs
                add constraint ck_webhook_delivery_jobs_status
                    check (status in ('PENDING', 'IN_PROGRESS', 'RETRY_SCHEDULED', 'SUCCEEDED', 'FAILED'));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_webhook_delivery_jobs_attempts') then
            alter table webhook_delivery_jobs
                add constraint ck_webhook_delivery_jobs_attempts
                    check (attempt_count >= 0 and max_attempts > 0 and attempt_count <= max_attempts);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_webhook_delivery_jobs_tenant') then
            alter table webhook_delivery_jobs
                add constraint fk_webhook_delivery_jobs_tenant
                    foreign key (tenant_id) references tenants (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_webhook_delivery_jobs_endpoint') then
            alter table webhook_delivery_jobs
                add constraint fk_webhook_delivery_jobs_endpoint
                    foreign key (endpoint_id) references webhook_endpoints (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_webhook_delivery_attempts_job') then
            alter table webhook_delivery_attempts
                add constraint fk_webhook_delivery_attempts_job
                    foreign key (delivery_job_id) references webhook_delivery_jobs (id) on delete cascade;
        end if;
    end
$$;
