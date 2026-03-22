-- ==========================================================
-- V14: KYC Verification tables
-- ==========================================================

-- Journey templates – define steps required for a given KYC flow
create table if not exists journey_templates
(
    id          uuid primary key,
    is_deleted  boolean                        not null default false,
    created_at  timestamp(6) with time zone    not null,
    updated_at  timestamp(6) with time zone    not null,
    deleted_at  timestamp(6) with time zone,
    tenant_id   uuid                           not null,
    name        varchar(255)                   not null,
    description varchar(1000),
    config_json jsonb,
    status      varchar(20)                    not null default 'ACTIVE'
);

create index if not exists ix_journey_templates_tenant on journey_templates (tenant_id);
create index if not exists ix_journey_templates_tenant_status on journey_templates (tenant_id, status);

-- Client identities – personal data of the individual being verified
create table if not exists client_identities
(
    id                   uuid primary key,
    is_deleted           boolean                        not null default false,
    created_at           timestamp(6) with time zone    not null,
    updated_at           timestamp(6) with time zone    not null,
    deleted_at           timestamp(6) with time zone,
    tenant_id            uuid                           not null,
    external_reference   varchar(255),
    first_name           text,
    last_name            text,
    date_of_birth        text,
    country_of_residence text,
    email                text,
    dial_code            text,
    phone                text,
    -- Document-extracted fields (populated after OCR)
    document_type        varchar(16),
    document_number      text,
    document_expires_at  text,
    sex                  text,
    national_number      text,
    issuing_country      text,
    nationality          text,
    place_of_birth       text,
    address              text
);

create index if not exists ix_client_identities_tenant on client_identities (tenant_id);
create index if not exists ix_client_identities_tenant_ext_ref
    on client_identities (tenant_id, external_reference);

-- Verifications – a single KYC check process for one client
create table if not exists verifications
(
    id                       uuid primary key,
    is_deleted               boolean                        not null default false,
    created_at               timestamp(6) with time zone    not null,
    updated_at               timestamp(6) with time zone    not null,
    deleted_at               timestamp(6) with time zone,
    tenant_id                uuid                           not null,
    journey_template_id      uuid                           not null,
    client_identity_id       uuid                           not null,
    status                   varchar(32)                    not null default 'INITIATED',
    verification_token_hash  varchar(128)                   not null,
    expires_at               timestamp(6) with time zone,
    completed_at             timestamp(6) with time zone,
    created_by_user_id       uuid
);

create unique index if not exists uq_verifications_token_hash on verifications (verification_token_hash);
create index if not exists ix_verifications_tenant on verifications (tenant_id);
create index if not exists ix_verifications_tenant_status on verifications (tenant_id, status);
create index if not exists ix_verifications_client_identity on verifications (client_identity_id);
create index if not exists ix_verifications_expires_at on verifications (expires_at) where status not in ('APPROVED','REJECTED','EXPIRED');

-- Check results – individual automated check outputs from the Python worker
create table if not exists check_results
(
    id              uuid primary key,
    is_deleted      boolean                        not null default false,
    created_at      timestamp(6) with time zone    not null,
    updated_at      timestamp(6) with time zone    not null,
    deleted_at      timestamp(6) with time zone,
    tenant_id       uuid                           not null,
    verification_id uuid                           not null,
    check_type      varchar(32)                    not null,
    status          varchar(16)                    not null,
    score           numeric(6, 4),
    details_json    jsonb
);

create index if not exists ix_check_results_verification on check_results (verification_id);
create index if not exists ix_check_results_tenant_type on check_results (tenant_id, check_type);

-- Risk scores – overall risk assessment aggregated from check results
create table if not exists risk_scores
(
    id              uuid primary key,
    is_deleted      boolean                        not null default false,
    created_at      timestamp(6) with time zone    not null,
    updated_at      timestamp(6) with time zone    not null,
    deleted_at      timestamp(6) with time zone,
    tenant_id       uuid                           not null,
    verification_id uuid                           not null,
    overall_score   integer                        not null,
    level           varchar(16)                    not null,
    breakdown_json  jsonb
);

create unique index if not exists uq_risk_scores_verification on risk_scores (verification_id);
create index if not exists ix_risk_scores_tenant on risk_scores (tenant_id);

-- Foreign keys and check constraints
do
$$
    begin
        if not exists (select 1 from pg_constraint where conname = 'fk_journey_templates_tenant') then
            alter table journey_templates
                add constraint fk_journey_templates_tenant
                    foreign key (tenant_id) references tenants (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_journey_templates_status') then
            alter table journey_templates
                add constraint ck_journey_templates_status
                    check (status in ('ACTIVE', 'ARCHIVED'));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_client_identities_tenant') then
            alter table client_identities
                add constraint fk_client_identities_tenant
                    foreign key (tenant_id) references tenants (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_client_identities_document_type') then
            alter table client_identities
                add constraint ck_client_identities_document_type
                    check (document_type in ('CZECH_ID', 'PASSPORT'));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_verifications_tenant') then
            alter table verifications
                add constraint fk_verifications_tenant
                    foreign key (tenant_id) references tenants (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_verifications_journey_template') then
            alter table verifications
                add constraint fk_verifications_journey_template
                    foreign key (journey_template_id) references journey_templates (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_verifications_client_identity') then
            alter table verifications
                add constraint fk_verifications_client_identity
                    foreign key (client_identity_id) references client_identities (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_verifications_created_by_user') then
            alter table verifications
                add constraint fk_verifications_created_by_user
                    foreign key (created_by_user_id) references users (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_verifications_status') then
            alter table verifications
                add constraint ck_verifications_status
                    check (status in (
                        'INITIATED', 'IN_PROGRESS', 'READY_FOR_AUTOCHECK',
                        'AUTO_PASSED', 'AUTO_FAILED', 'REQUIRES_REVIEW',
                        'APPROVED', 'REJECTED', 'EXPIRED'
                    ));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_check_results_tenant') then
            alter table check_results
                add constraint fk_check_results_tenant
                    foreign key (tenant_id) references tenants (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_check_results_verification') then
            alter table check_results
                add constraint fk_check_results_verification
                    foreign key (verification_id) references verifications (id) on delete cascade;
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_check_results_check_type') then
            alter table check_results
                add constraint ck_check_results_check_type
                    check (check_type in (
                        'DOC_OCR', 'FACE_MATCH',
                        'LIVENESS', 'SANCTIONS', 'PEP'
                    ));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_check_results_status') then
            alter table check_results
                add constraint ck_check_results_status
                    check (status in ('PASSED', 'FAILED', 'WARNING', 'ERROR'));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_risk_scores_tenant') then
            alter table risk_scores
                add constraint fk_risk_scores_tenant
                    foreign key (tenant_id) references tenants (id);
        end if;

        if not exists (select 1 from pg_constraint where conname = 'fk_risk_scores_verification') then
            alter table risk_scores
                add constraint fk_risk_scores_verification
                    foreign key (verification_id) references verifications (id) on delete cascade;
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_risk_scores_level') then
            alter table risk_scores
                add constraint ck_risk_scores_level
                    check (level in ('LOW', 'MEDIUM', 'HIGH'));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_risk_scores_overall_score') then
            alter table risk_scores
                add constraint ck_risk_scores_overall_score
                    check (overall_score >= 0 and overall_score <= 100);
        end if;
    end
$$;