alter table if exists nfc_game_template
    add column if not exists dashboard_metric_max numeric(14,2);
