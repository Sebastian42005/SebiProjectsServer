alter table if exists nfc_game_template
    add column if not exists blocked_reason text;

create table if not exists nfc_game_rating (
    id uuid primary key,
    game_template_id uuid not null,
    account_id bigint not null,
    rating integer not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uk_nfc_game_rating_game_account unique (game_template_id, account_id),
    constraint chk_nfc_game_rating_value check (rating between 1 and 5)
);

create index if not exists idx_nfc_game_rating_game on nfc_game_rating(game_template_id);
create index if not exists idx_nfc_game_rating_account on nfc_game_rating(account_id);
