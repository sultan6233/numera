-- Numerology backend initial schema
-- Note: birth_date is sensitive personal data -> stored as application-layer
-- AES-GCM encrypted base64 text (column birth_date_enc), never as a plain
-- SQL date column. This also means we cannot do date arithmetic in SQL on it;
-- all such logic happens in the application after decryption.

create extension if not exists pgcrypto;

create table users (
    id uuid primary key default gen_random_uuid(),
    device_id text not null unique,
    name text,
    birth_date_enc text,               -- AES-GCM encrypted, base64
    language text not null default 'ru',
    timezone text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table companions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    name text not null,
    birth_date_enc text not null,      -- AES-GCM encrypted, base64
    relation_label text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_companions_user_id on companions(user_id);

create table computed_numbers (
    user_id uuid primary key references users(id) on delete cascade,
    life_path int,
    expression int,
    soul_urge int,
    personality int,
    birth_day int,
    health_code int,
    business_code int,
    updated_at timestamptz not null default now()
);

create table daily_insights (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    date date not null,
    personal_day_number int not null,
    focus_area text not null,
    headline text not null,
    greeting text,
    body jsonb not null,
    suggested_action text,
    affirmation text,
    lucky_number int,
    source text not null default 'llm', -- 'llm' | 'fallback'
    created_at timestamptz not null default now(),
    unique(user_id, date)
);

create index idx_daily_insights_date on daily_insights(date);
create index idx_daily_insights_user_date on daily_insights(user_id, date desc);

create table subscriptions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references users(id) on delete set null,
    platform text not null,             -- 'apple' | 'google'
    product_id text,
    status text not null,               -- active / expired / cancelled / refunded / grace_period / on_hold
    expires_at timestamptz,
    original_transaction_id text not null,
    latest_transaction_id text,
    raw_payload jsonb,
    updated_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    unique(platform, original_transaction_id)
);

create index idx_subscriptions_user_id on subscriptions(user_id);
create index idx_subscriptions_status on subscriptions(status);

-- Idempotency ledger for webhook notifications (Apple notificationUUID / Google messageId)
create table webhook_events (
    id uuid primary key default gen_random_uuid(),
    platform text not null,             -- 'apple' | 'google'
    event_id text not null,             -- notificationUUID / Pub/Sub messageId
    received_at timestamptz not null default now(),
    unique(platform, event_id)
);

create table push_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    platform text not null,             -- 'ios' | 'android'
    token text not null,
    updated_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    unique(platform, token)
);

create index idx_push_tokens_user_id on push_tokens(user_id);

create table remote_config (
    key text primary key,
    value jsonb not null,
    version int not null default 1,
    updated_at timestamptz not null default now()
);

-- default remote config values are seeded by V2 migration.
