alter table if exists nfc_player
    add column if not exists image_content bytea,
    add column if not exists image_content_type varchar(255),
    add column if not exists image_file_name varchar(255);

alter table if exists nfc_game_template
    add column if not exists image_content bytea,
    add column if not exists image_content_type varchar(255),
    add column if not exists image_file_name varchar(255);
