alter table nfc_device add column if not exists pairing_code varchar(16);

create unique index if not exists uk_nfc_device_pairing_code on nfc_device(pairing_code);
