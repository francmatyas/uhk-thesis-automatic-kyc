create table if not exists session_switch_audits
(
    id             uuid primary key,
    is_deleted     boolean,
    created_at     timestamp(6) with time zone not null,
    updated_at     timestamp(6) with time zone not null,
    deleted_at     timestamp(6) with time zone,
    user_id        uuid                           not null,
    session_id     uuid,
    from_tenant_id uuid,
    to_tenant_id   uuid,
    previous_jti   varchar(64),
    new_jti        varchar(64)                    not null,
    ip_address     varchar(64),
    user_agent     varchar(512),
    switch_source  varchar(64)                    not null
);

create index if not exists ix_session_switch_audits_user on session_switch_audits (user_id);
create index if not exists ix_session_switch_audits_session on session_switch_audits (session_id);
create index if not exists ix_session_switch_audits_created on session_switch_audits (created_at);

do
$$
    begin
        if not exists (select 1
                       from pg_constraint
                       where conname = 'fk_session_switch_audits_user') then
            alter table session_switch_audits
                add constraint fk_session_switch_audits_user
                    foreign key (user_id) references users (id);
        end if;

        if not exists (select 1
                       from pg_constraint
                       where conname = 'fk_session_switch_audits_session') then
            alter table session_switch_audits
                add constraint fk_session_switch_audits_session
                    foreign key (session_id) references user_sessions (id);
        end if;
    end
$$;
