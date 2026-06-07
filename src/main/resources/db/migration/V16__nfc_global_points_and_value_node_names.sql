alter table if exists nfc_game_template
    add column if not exists global_winner_points bigint not null default 5,
    add column if not exists global_second_place_points bigint,
    add column if not exists global_third_place_points bigint;

update nfc_flow_node
set type = 'CHANGE_VALUE'
where type = 'AWARD_POINTS';

update nfc_flow_node
set type = 'ADD_GLOBAL_POINTS'
where type = 'AWARD_ROUND_WIN';
