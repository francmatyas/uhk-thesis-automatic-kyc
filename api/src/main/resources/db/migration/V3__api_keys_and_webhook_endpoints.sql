create table if not exists api_keys
(
    id          uuid primary key,
    is_deleted  boolean,
    created_at  timestamp(6) with time zone not null,
    updated_at  timestamp(6) with time zone not null,
    deleted_at  timestamp(6) with time zone,
    tenant_id   uuid                           not null,
    name        varchar(255)                   not null,
    public_key  varchar(128)                   not null,
    secret_hash varchar(255)                   not null,
    status      varchar(20)                    not null,
    last_used_at timestamp(6) with time zone
);

create unique index if not exists uq_api_keys_public_key on api_keys (public_key);
create index if not exists ix_api_keys_tenant on api_keys (tenant_id);
create index if not exists ix_api_keys_status on api_keys (status);

create table if not exists webhook_endpoints
(
    id               uuid primary key,
    is_deleted       boolean,
    created_at       timestamp(6) with time zone not null,
    updated_at       timestamp(6) with time zone not null,
    deleted_at       timestamp(6) with time zone,
    tenant_id        uuid                           not null,
    url              text                           not null,
    secret           varchar(255)                   not null,
    status           varchar(20)                    not null,
    last_delivery_at timestamp(6) with time zone
);

create index if not exists ix_webhook_endpoints_tenant on webhook_endpoints (tenant_id);
create index if not exists ix_webhook_endpoints_status on webhook_endpoints (status);

do
$$
    begin
        if not exists (select 1
                       from pg_constraint
                       where conname = 'fk_api_keys_tenant') then
            alter table api_keys
                add constraint fk_api_keys_tenant
                    foreign key (tenant_id) references tenants (id);
        end if;

        if not exists (select 1
                       from pg_constraint
                       where conname = 'fk_webhook_endpoints_tenant') then
            alter table webhook_endpoints
                add constraint fk_webhook_endpoints_tenant
                    foreign key (tenant_id) references tenants (id);
        end if;

        if not exists (select 1
                       from pg_constraint
                       where conname = 'ck_api_keys_status') then
            alter table api_keys
                add constraint ck_api_keys_status
                    check (status in ('ACTIVE', 'REVOKED'));
        end if;

        if not exists (select 1
                       from pg_constraint
                       where conname = 'ck_webhook_endpoints_status') then
            alter table webhook_endpoints
                add constraint ck_webhook_endpoints_status
                    check (status in ('ACTIVE', 'DISABLED'));
        end if;
    end
$$;

