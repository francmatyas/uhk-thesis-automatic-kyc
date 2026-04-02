-- ==========================================================
-- V16: Verification steps and OTPs
-- ==========================================================

-- verification_steps – tracks progress of every step (core + optional) per verification
create table if not exists verification_steps
(
    id              uuid primary key,
    is_deleted      boolean                     not null default false,
    created_at      timestamp(6) with time zone not null,
    updated_at      timestamp(6) with time zone not null,
    deleted_at      timestamp(6) with time zone,
    tenant_id       uuid                        not null,
    verification_id uuid                        not null,
    step_type       varchar(32)                 not null,
    status          varchar(16)                 not null default 'PENDING',
    completed_at    timestamp(6) with time zone,
    details_json    jsonb
);

create index if not exists ix_verification_steps_verification on verification_steps (verification_id);
create index if not exists ix_verification_steps_tenant on verification_steps (tenant_id);
create unique index if not exists uq_verification_steps_type
    on verification_steps (verification_id, step_type)
    where is_deleted = false;

-- verification_otps – short-lived OTP codes for email/phone verification
create table if not exists verification_otps
(
    id              uuid primary key,
    is_deleted      boolean                     not null default false,
    created_at      timestamp(6) with time zone not null,
    updated_at      timestamp(6) with time zone not null,
    deleted_at      timestamp(6) with time zone,
    tenant_id       uuid                        not null,
    verification_id uuid                        not null,
    type            varchar(8)                  not null,
    code_hash       varchar(128)                not null,
    contact         text,
    expires_at      timestamp(6) with time zone not null,
    verified_at     timestamp(6) with time zone,
    attempts        integer                     not null default 0
);

create index if not exists ix_verification_otps_verification on verification_otps (verification_id);

-- Extend check_type constraint to include optional step types
do
$$
    begin
        if exists (select 1 from pg_constraint where conname = 'ck_check_results_check_type') then
            alter table check_results drop constraint ck_check_results_check_type;
        end if;

        alter table check_results
            add constraint ck_check_results_check_type
                check (check_type in (
                    'DOC_OCR', 'FACE_MATCH', 'LIVENESS', 'SANCTIONS', 'PEP',
                    'EMAIL_VERIFICATION', 'PHONE_VERIFICATION', 'AML_QUESTIONNAIRE'
                ));

        if not exists (select 1 from pg_constraint where conname = 'fk_verification_steps_tenant') then
            alter table verification_steps
                add constraint fk_verification_steps_tenant
                    foreign key (tenant_id) references tenants (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_verification_steps_verification') then
            alter table verification_steps
                add constraint fk_verification_steps_verification
                    foreign key (verification_id) references verifications (id) on delete cascade;
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_verification_steps_step_type') then
            alter table verification_steps
                add constraint ck_verification_steps_step_type
                    check (step_type in (
                        'DOC_OCR', 'FACE_MATCH', 'LIVENESS', 'AML_SCREEN',
                        'EMAIL_VERIFICATION', 'PHONE_VERIFICATION', 'AML_QUESTIONNAIRE'
                    ));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_verification_steps_status') then
            alter table verification_steps
                add constraint ck_verification_steps_status
                    check (status in ('PENDING', 'COMPLETED', 'SKIPPED', 'FAILED'));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_verification_otps_tenant') then
            alter table verification_otps
                add constraint fk_verification_otps_tenant
                    foreign key (tenant_id) references tenants (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_verification_otps_verification') then
            alter table verification_otps
                add constraint fk_verification_otps_verification
                    foreign key (verification_id) references verifications (id) on delete cascade;
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_verification_otps_type') then
            alter table verification_otps
                add constraint ck_verification_otps_type
                    check (type in ('EMAIL', 'PHONE'));
        end if;
    end
$$;