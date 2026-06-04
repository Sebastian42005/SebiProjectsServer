alter table if exists nfc_card
    drop constraint if exists ck_nfc_card_assignment;

update nfc_card
set card_type = 'UNKNOWN'
where status = 'UNASSIGNED'
  and player_id is null
  and game_template_id is null;

alter table if exists nfc_card
    add constraint ck_nfc_card_assignment check (
        (status <> 'ASSIGNED' and player_id is null and game_template_id is null)
        or (status = 'ASSIGNED' and card_type = 'PLAYER' and player_id is not null and game_template_id is null)
        or (status = 'ASSIGNED' and card_type = 'GAME' and game_template_id is not null and player_id is null)
    );
