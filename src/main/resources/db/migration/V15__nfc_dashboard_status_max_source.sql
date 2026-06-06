alter table if exists nfc_game_template
    add column if not exists dashboard_status_max_source varchar(255) default 'roundLimit';
