insert into nfc_device (id, name, device_key, active, last_seen_at, created_at)
values ('10000000-0000-0000-0000-000000000001', 'esp32-1', 'secret-key', true, null, now())
on conflict (name) do nothing;
