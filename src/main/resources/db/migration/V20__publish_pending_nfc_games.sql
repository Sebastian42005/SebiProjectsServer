update nfc_game_template
set publication_status = 'PUBLISHED',
    blocked_reason = null
where publication_status = 'PENDING_REVIEW'
  and active = true;
