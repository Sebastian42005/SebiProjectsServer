create table if not exists nfc_admin_user (
    id uuid primary key,
    username varchar(255) not null unique,
    password_hash varchar(255) not null,
    role varchar(40) not null,
    active boolean not null,
    created_at timestamptz not null
);

create table if not exists nfc_device (
    id uuid primary key,
    name varchar(255) not null unique,
    device_key varchar(255) not null,
    active boolean not null,
    last_seen_at timestamptz,
    created_at timestamptz not null
);

create table if not exists nfc_player (
    id uuid primary key,
    name varchar(255) not null,
    description text,
    image_url varchar(255),
    active boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists nfc_game_template (
    id uuid primary key,
    name varchar(255) not null,
    description text,
    image_url varchar(255),
    active boolean not null,
    allow_teams boolean not null,
    min_team_size integer not null,
    max_team_size integer not null,
    supports_round_limit boolean not null,
    economy_enabled boolean not null,
    start_capital numeric(14,2) not null,
    small_step numeric(14,2) not null,
    large_step numeric(14,2) not null,
    win_rule_type varchar(80) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists nfc_card (
    id uuid primary key,
    card_uid varchar(255) not null unique,
    card_type varchar(40) not null,
    status varchar(40) not null,
    player_id uuid,
    game_template_id uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_nfc_card_assignment check (
        (status <> 'ASSIGNED' and player_id is null and game_template_id is null)
        or (status = 'ASSIGNED' and card_type = 'PLAYER' and player_id is not null and game_template_id is null)
        or (status = 'ASSIGNED' and card_type = 'GAME' and game_template_id is not null and player_id is null)
    )
);

create table if not exists nfc_flow_definition (
    id uuid primary key,
    game_template_id uuid not null,
    version integer not null,
    active boolean not null,
    start_state_key varchar(255) not null,
    created_at timestamptz not null
);

create table if not exists nfc_flow_state (
    id uuid primary key,
    flow_definition_id uuid not null,
    state_key varchar(255) not null,
    state_type varchar(255) not null,
    title varchar(255) not null,
    subtitle varchar(255),
    config_json jsonb not null,
    sort_order integer not null,
    constraint uk_nfc_flow_state_key unique (flow_definition_id, state_key)
);

create table if not exists nfc_flow_transition (
    id uuid primary key,
    flow_definition_id uuid not null,
    from_state_key varchar(255) not null,
    event_type varchar(255) not null,
    condition_json jsonb not null,
    action_json jsonb not null,
    to_state_key varchar(255) not null,
    sort_order integer not null
);

create table if not exists nfc_game_session (
    id uuid primary key,
    game_template_id uuid not null,
    device_id uuid not null,
    status varchar(60) not null,
    current_state_key varchar(255) not null,
    round_limit_type varchar(60) not null,
    round_limit integer,
    current_round_number integer not null,
    created_at timestamptz not null,
    started_at timestamptz,
    ended_at timestamptz
);

create table if not exists nfc_session_team (
    id uuid primary key,
    session_id uuid not null,
    name varchar(255) not null,
    team_order integer not null,
    target_size integer not null,
    status varchar(80) not null,
    created_at timestamptz not null
);

create table if not exists nfc_session_team_member (
    id uuid primary key,
    session_team_id uuid not null,
    player_id uuid not null,
    joined_at timestamptz not null,
    constraint uk_nfc_session_team_member_team_player unique (session_team_id, player_id)
);

create table if not exists nfc_session_round (
    id uuid primary key,
    session_id uuid not null,
    round_number integer not null,
    winning_team_id uuid,
    awarded_points_per_member integer not null,
    created_at timestamptz not null
);

create table if not exists nfc_session_account (
    id uuid primary key,
    session_id uuid not null,
    owner_type varchar(40) not null,
    team_id uuid,
    balance numeric(14,2) not null,
    created_at timestamptz not null
);

create table if not exists nfc_money_transaction (
    id uuid primary key,
    session_id uuid not null,
    from_account_id uuid not null,
    to_account_id uuid not null,
    amount numeric(14,2) not null,
    initiated_by_player_id uuid,
    created_at timestamptz not null
);

create table if not exists nfc_game_result (
    id uuid primary key,
    session_id uuid not null unique,
    winning_team_id uuid,
    end_reason varchar(255) not null,
    created_at timestamptz not null
);

create table if not exists nfc_session_event (
    id uuid primary key,
    session_id uuid,
    device_id uuid not null,
    event_type varchar(80) not null,
    payload_json jsonb not null,
    created_at timestamptz not null
);

create table if not exists nfc_player_stats_projection (
    player_id uuid primary key,
    games_played bigint not null,
    games_won bigint not null,
    rounds_won bigint not null,
    total_points bigint not null,
    win_rate double precision not null,
    updated_at timestamptz not null
);

create index if not exists idx_nfc_card_player on nfc_card(player_id);
create index if not exists idx_nfc_card_game_template on nfc_card(game_template_id);
create index if not exists idx_nfc_game_session_status on nfc_game_session(status);
create index if not exists idx_nfc_session_team_session on nfc_session_team(session_id);
create index if not exists idx_nfc_session_event_session on nfc_session_event(session_id);
