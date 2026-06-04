alter table if exists nfc_game_template
    add column if not exists publication_status varchar(40) not null default 'DRAFT',
    add column if not exists flow_version integer not null default 1,
    add column if not exists start_node_id uuid;

create table if not exists nfc_flow_node (
    id uuid primary key,
    game_template_id uuid not null,
    type varchar(120) not null,
    title varchar(255) not null,
    x integer not null,
    y integer not null,
    config_json jsonb not null default '{}'::jsonb,
    ui_config_json jsonb not null default '{}'::jsonb,
    sort_order integer not null default 0
);

create table if not exists nfc_flow_edge (
    id uuid primary key,
    game_template_id uuid not null,
    source_node_id uuid not null,
    target_node_id uuid not null,
    event_type varchar(120) not null,
    condition_type varchar(120),
    condition_config_json jsonb not null default '{}'::jsonb,
    priority integer not null default 0
);

create index if not exists idx_nfc_flow_node_game_template on nfc_flow_node(game_template_id);
create index if not exists idx_nfc_flow_node_type on nfc_flow_node(type);
create index if not exists idx_nfc_flow_edge_game_template on nfc_flow_edge(game_template_id);
create index if not exists idx_nfc_flow_edge_source on nfc_flow_edge(source_node_id);
create index if not exists idx_nfc_flow_edge_target on nfc_flow_edge(target_node_id);
