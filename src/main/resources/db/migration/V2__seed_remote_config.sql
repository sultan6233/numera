insert into remote_config (key, value, version) values
    ('paywall', '{
        "variant": "control",
        "trial_days": 3,
        "products": [
            {"id": "premium_weekly", "highlighted": false},
            {"id": "premium_yearly", "highlighted": true}
        ]
    }'::jsonb, 1),
    ('feature_flags', '{
        "companions_enabled": true,
        "daily_push_enabled": true,
        "onboarding_v2": false
    }'::jsonb, 1)
on conflict (key) do nothing;
