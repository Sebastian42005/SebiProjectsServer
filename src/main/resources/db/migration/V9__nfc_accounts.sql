alter table nfc_device add column if not exists account_id bigint;
alter table nfc_player add column if not exists account_id bigint;
alter table nfc_game_template add column if not exists account_id bigint;
alter table nfc_card add column if not exists account_id bigint;
alter table nfc_game_session add column if not exists account_id bigint;

create index if not exists idx_nfc_device_account on nfc_device(account_id);
create index if not exists idx_nfc_player_account on nfc_player(account_id);
create index if not exists idx_nfc_game_template_account on nfc_game_template(account_id);
create index if not exists idx_nfc_card_account on nfc_card(account_id);
create index if not exists idx_nfc_game_session_account_status on nfc_game_session(account_id, status);
