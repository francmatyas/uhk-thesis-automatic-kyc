create table if not exists audit_logs
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone not null,
    tenant_id        uuid,
    actor_user_id    uuid,
    actor_type       varchar(16)                    not null,
    actor_api_key_id uuid,
    entity_type      varchar(64)                    not null,
    entity_id        text                           not null,
    action           varchar(64)                    not null,
    old_value        jsonb,
    new_value        jsonb,
    metadata         jsonb                          not null default '{}'::jsonb,
    ip_address       inet,
    user_agent       text,
    correlation_id   uuid,
    request_id       varchar(128),
    result           varchar(16)                    not null default 'SUCCESS',
    error_code       varchar(64)
);

create index if not exists ix_audit_logs_tenant_created on audit_logs (tenant_id, created_at desc);
create index if not exists ix_audit_logs_entity_created on audit_logs (entity_type, entity_id, created_at desc);
create index if not exists ix_audit_logs_actor_user_created on audit_logs (actor_user_id, created_at desc);
create index if not exists ix_audit_logs_correlation on audit_logs (correlation_id);
create index if not exists ix_audit_logs_created on audit_logs (created_at desc);

do
$$
    begin
        if not exists (select 1 from pg_constraint where conname = 'ck_audit_logs_actor_type') then
            alter table audit_logs
                add constraint ck_audit_logs_actor_type
                    check (actor_type in ('USER', 'API_KEY', 'SYSTEM', 'SERVICE'));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_audit_logs_result') then
            alter table audit_logs
                add constraint ck_audit_logs_result
                    check (result in ('SUCCESS', 'FAILURE'));
        end if;

        if not exists (select 1 from pg_constraint where conname = 'ck_audit_logs_actor_ref') then
            alter table audit_logs
                add constraint ck_audit_logs_actor_ref
                    check (
                        (actor_type = 'USER' and actor_user_id is not null and actor_api_key_id is null)
                            or (actor_type = 'API_KEY' and actor_api_key_id is not null and actor_user_id is null)
                            or (actor_type in ('SYSTEM', 'SERVICE') and actor_user_id is null and actor_api_key_id is null)
                        );
        end if;

        if exists (select 1
                   from information_schema.tables
                   where table_schema = 'public'
                     and table_name = 'tenants')
            and not exists (select 1 from pg_constraint where conname = 'fk_audit_logs_tenant') then
            alter table audit_logs
                add constraint fk_audit_logs_tenant
                    foreign key (tenant_id) references tenants (id) on delete set null;
        end if;

        if exists (select 1
                   from information_schema.tables
                   where table_schema = 'public'
                     and table_name = 'users')
            and not exists (select 1 from pg_constraint where conname = 'fk_audit_logs_actor_user') then
            alter table audit_logs
                add constraint fk_audit_logs_actor_user
                    foreign key (actor_user_id) references users (id) on delete set null;
        end if;

        if exists (select 1
                   from information_schema.tables
                   where table_schema = 'public'
                     and table_name = 'api_keys')
            and not exists (select 1 from pg_constraint where conname = 'fk_audit_logs_actor_api_key') then
            alter table audit_logs
                add constraint fk_audit_logs_actor_api_key
                    foreign key (actor_api_key_id) references api_keys (id) on delete set null;
        end if;
    end
$$;

create or replace function prevent_audit_logs_mutation()
    returns trigger
    language plpgsql
as
$$
begin
    raise exception 'audit_logs is append-only';
end;
$$;

do
$$
    begin
        if not exists (select 1
                       from pg_trigger
                       where tgname = 'trg_audit_logs_no_update'
                         and tgrelid = 'audit_logs'::regclass
                         and not tgisinternal) then
            create trigger trg_audit_logs_no_update
                before update
                on audit_logs
                for each row
            execute function prevent_audit_logs_mutation();
        end if;

        if not exists (select 1
                       from pg_trigger
                       where tgname = 'trg_audit_logs_no_delete'
                         and tgrelid = 'audit_logs'::regclass
                         and not tgisinternal) then
            create trigger trg_audit_logs_no_delete
                before delete
                on audit_logs
                for each row
            execute function prevent_audit_logs_mutation();
        end if;
    end
$$;
