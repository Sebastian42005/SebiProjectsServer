create table if not exists habit (
    id bigserial primary key,
    name varchar(255) not null,
    frequency varchar(32) not null,
    target_count integer not null,
    reward_minutes integer not null,
    active boolean not null default true,
    created_at bigint not null,
    updated_at bigint not null
);

create table if not exists habit_completion (
    id bigserial primary key,
    habit_id bigint not null references habit(id),
    completion_date date not null,
    completed_at timestamp with time zone not null,
    reward_minutes integer not null,
    request_key varchar(255) not null,
    undone boolean not null default false,
    undone_at timestamp with time zone null,
    constraint uk_habit_completion_request_key unique (request_key)
);

create index if not exists ix_habit_completion_habit_date on habit_completion(habit_id, completion_date);
create index if not exists ix_habit_completion_date on habit_completion(completion_date);

create table if not exists instagram_account (
    id bigint primary key,
    available_minutes integer not null default 0,
    active_unlock_until timestamp with time zone null,
    lock_published_at timestamp with time zone null,
    updated_at timestamp with time zone not null
);

insert into instagram_account (id, available_minutes, updated_at)
values (1, 0, now())
on conflict (id) do nothing;

create table if not exists instagram_redemption (
    id bigserial primary key,
    request_key varchar(255) not null,
    minutes integer not null,
    redeemed_at timestamp with time zone not null,
    unlocked_until timestamp with time zone not null,
    constraint uk_instagram_redemption_request_key unique (request_key)
);
