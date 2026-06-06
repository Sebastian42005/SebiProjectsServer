alter table if exists nfc_game_template
    add column if not exists dashboard_metric_display_type varchar(60) default 'RACE_BAR',
    add column if not exists dashboard_status_source varchar(255) default 'currentRound',
    add column if not exists dashboard_status_label varchar(255) default 'Runde',
    add column if not exists dashboard_status_suffix varchar(255),
    add column if not exists dashboard_status_display_type varchar(60) default 'PROGRESS_BAR';
