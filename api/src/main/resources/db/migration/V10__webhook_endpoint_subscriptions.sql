create table if not exists webhook_endpoint_subscriptions
(
    id         uuid primary key,
    created_at timestamp(6) with time zone not null,
    endpoint_id uuid                          not null,
    event_type varchar(64)                    not null,
    enabled    boolean                        not null default true
);

create unique index if not exists uq_webhook_endpoint_subscriptions_endpoint_event
    on webhook_endpoint_subscriptions (endpoint_id, event_type);
create index if not exists ix_webhook_endpoint_subscriptions_endpoint
    on webhook_endpoint_subscriptions (endpoint_id);
create index if not exists ix_webhook_endpoint_subscriptions_event_enabled
    on webhook_endpoint_subscriptions (event_type, enabled);

do
$$
    begin
        if not exists (select 1
                       from pg_constraint
                       where conname = 'fk_webhook_endpoint_subscriptions_endpoint') then
            alter table webhook_endpoint_subscriptions
                add constraint fk_webhook_endpoint_subscriptions_endpoint
                    foreign key (endpoint_id) references webhook_endpoints (id);
        end if;

        if not exists (select 1
                       from pg_constraint
                       where conname = 'ck_webhook_endpoint_subscriptions_event_type') then
            alter table webhook_endpoint_subscriptions
                add constraint ck_webhook_endpoint_subscriptions_event_type
                    check (event_type in ('DOCUMENT_READY', 'DOCUMENT_DELETED'));
        end if;
    end
$$;
