create table if not exists stored_documents
(
    id                  uuid primary key,
    is_deleted          boolean,
    created_at          timestamp(6) with time zone not null,
    updated_at          timestamp(6) with time zone not null,
    deleted_at          timestamp(6) with time zone,
    owner_type          varchar(64)                    not null,
    owner_id            uuid                           not null,
    tenant_id           uuid,
    category            varchar(64)                    not null,
    kind                varchar(32)                    not null,
    status              varchar(32)                    not null,
    storage_key         varchar(1024)                  not null,
    original_filename   varchar(512)                   not null,
    content_type        varchar(255)                   not null,
    size_bytes          bigint,
    checksum            varchar(128),
    upload_expires_at   timestamp(6) with time zone,
    uploaded_by_user_id uuid
);

create index if not exists ix_stored_documents_owner on stored_documents (owner_type, owner_id);
create index if not exists ix_stored_documents_tenant on stored_documents (tenant_id);
create index if not exists ix_stored_documents_status on stored_documents (status);
create index if not exists ix_stored_documents_category on stored_documents (category);
create index if not exists ix_stored_documents_storage_key on stored_documents (storage_key);

do
$$
    begin
        if exists (select 1
                   from information_schema.tables
                   where table_schema = 'public'
                     and table_name = 'users')
            and not exists (select 1
                            from pg_constraint
                            where conname = 'fk_stored_documents_uploaded_by_user') then
            alter table stored_documents
                add constraint fk_stored_documents_uploaded_by_user
                    foreign key (uploaded_by_user_id) references users (id);
        end if;

        if not exists (select 1
                       from pg_constraint
                       where conname = 'ck_stored_documents_kind') then
            alter table stored_documents
                add constraint ck_stored_documents_kind
                    check (kind in ('UPLOADED', 'GENERATED'));
        end if;

        if not exists (select 1
                       from pg_constraint
                       where conname = 'ck_stored_documents_status') then
            alter table stored_documents
                add constraint ck_stored_documents_status
                    check (status in ('PENDING_UPLOAD', 'READY', 'FAILED', 'DELETED'));
        end if;
    end
$$;
