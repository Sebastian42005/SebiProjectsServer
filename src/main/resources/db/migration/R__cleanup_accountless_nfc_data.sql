-- Removes legacy NFC game data that was created before account ownership existed.
-- Account-owned rows are preserved. Dependent rows are deleted first so repeated
-- runs stay safe even if database-level foreign key constraints are added later.

drop table if exists nfc_cleanup_target_cards;
drop table if exists nfc_cleanup_target_flow_definitions;
drop table if exists nfc_cleanup_target_session_accounts;
drop table if exists nfc_cleanup_target_teams;
drop table if exists nfc_cleanup_target_sessions;
drop table if exists nfc_cleanup_target_devices;
drop table if exists nfc_cleanup_target_games;
drop table if exists nfc_cleanup_target_players;
drop table if exists nfc_cleanup_existing_accounts;

create temporary table nfc_cleanup_existing_accounts (
    id bigint primary key
);

do $$
begin
    if to_regclass('public.app_user') is not null then
        insert into nfc_cleanup_existing_accounts(id)
        select id from app_user;
    end if;
end $$;

create temporary table nfc_cleanup_target_players as
select p.id
from nfc_player p
left join nfc_cleanup_existing_accounts u on u.id = p.account_id
where p.account_id is null
   or u.id is null;

create temporary table nfc_cleanup_target_games as
select g.id
from nfc_game_template g
left join nfc_cleanup_existing_accounts u on u.id = g.account_id
where g.account_id is null
   or u.id is null;

create temporary table nfc_cleanup_target_devices as
select d.id
from nfc_device d
left join nfc_cleanup_existing_accounts u on u.id = d.account_id
where d.account_id is null
   or u.id is null;

create temporary table nfc_cleanup_target_sessions as
select s.id
from nfc_game_session s
left join nfc_game_template g on g.id = s.game_template_id
left join nfc_device d on d.id = s.device_id
left join nfc_cleanup_existing_accounts u on u.id = s.account_id
where s.account_id is null
   or u.id is null
   or g.account_id is null
   or d.account_id is null;

create temporary table nfc_cleanup_target_teams as
select id
from nfc_session_team
where session_id in (select id from nfc_cleanup_target_sessions);

create temporary table nfc_cleanup_target_session_accounts as
select id
from nfc_session_account
where session_id in (select id from nfc_cleanup_target_sessions)
   or team_id in (select id from nfc_cleanup_target_teams);

create temporary table nfc_cleanup_target_flow_definitions as
select id
from nfc_flow_definition
where game_template_id in (select id from nfc_cleanup_target_games);

create temporary table nfc_cleanup_target_cards as
select c.id
from nfc_card c
left join nfc_cleanup_existing_accounts u on u.id = c.account_id
where c.account_id is null
   or u.id is null
   or c.player_id in (select id from nfc_cleanup_target_players)
   or c.game_template_id in (select id from nfc_cleanup_target_games);

delete from nfc_money_transaction
where session_id in (select id from nfc_cleanup_target_sessions)
   or from_account_id in (select id from nfc_cleanup_target_session_accounts)
   or to_account_id in (select id from nfc_cleanup_target_session_accounts)
   or initiated_by_player_id in (select id from nfc_cleanup_target_players);

delete from nfc_game_result
where session_id in (select id from nfc_cleanup_target_sessions)
   or winning_team_id in (select id from nfc_cleanup_target_teams);

delete from nfc_session_round
where session_id in (select id from nfc_cleanup_target_sessions)
   or winning_team_id in (select id from nfc_cleanup_target_teams);

delete from nfc_session_value
where session_id in (select id from nfc_cleanup_target_sessions)
   or owner_id in (select id from nfc_cleanup_target_teams)
   or owner_id in (select id from nfc_cleanup_target_session_accounts);

delete from nfc_session_team_member
where session_team_id in (select id from nfc_cleanup_target_teams)
   or player_id in (select id from nfc_cleanup_target_players);

delete from nfc_session_account
where id in (select id from nfc_cleanup_target_session_accounts);

delete from nfc_session_event
where session_id in (select id from nfc_cleanup_target_sessions)
   or device_id in (select id from nfc_cleanup_target_devices);

delete from nfc_session_team
where id in (select id from nfc_cleanup_target_teams);

delete from nfc_game_session
where id in (select id from nfc_cleanup_target_sessions);

delete from nfc_flow_transition
where flow_definition_id in (select id from nfc_cleanup_target_flow_definitions);

delete from nfc_flow_state
where flow_definition_id in (select id from nfc_cleanup_target_flow_definitions);

delete from nfc_flow_definition
where id in (select id from nfc_cleanup_target_flow_definitions);

delete from nfc_flow_edge
where game_template_id in (select id from nfc_cleanup_target_games);

delete from nfc_flow_node
where game_template_id in (select id from nfc_cleanup_target_games);

delete from nfc_card
where id in (select id from nfc_cleanup_target_cards);

delete from nfc_player_stats_projection
where player_id in (select id from nfc_cleanup_target_players);

delete from nfc_player
where id in (select id from nfc_cleanup_target_players);

delete from nfc_game_template
where id in (select id from nfc_cleanup_target_games);

delete from nfc_device
where id in (select id from nfc_cleanup_target_devices);

drop table if exists nfc_cleanup_target_cards;
drop table if exists nfc_cleanup_target_flow_definitions;
drop table if exists nfc_cleanup_target_session_accounts;
drop table if exists nfc_cleanup_target_teams;
drop table if exists nfc_cleanup_target_sessions;
drop table if exists nfc_cleanup_target_devices;
drop table if exists nfc_cleanup_target_games;
drop table if exists nfc_cleanup_target_players;
drop table if exists nfc_cleanup_existing_accounts;
