create table if not exists nfc_session_value (
    id uuid primary key,
    session_id uuid not null,
    owner_type varchar(255) not null,
    owner_id uuid not null,
    value_key varchar(255) not null,
    value numeric(14, 2) not null default 0,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uk_nfc_session_value_owner_key unique (session_id, owner_type, owner_id, value_key)
);

create index if not exists idx_nfc_session_value_session on nfc_session_value (session_id);
create index if not exists idx_nfc_session_value_key on nfc_session_value (value_key);
