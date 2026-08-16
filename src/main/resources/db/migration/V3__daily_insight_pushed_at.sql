-- Tracks whether the daily push notification for this (user, date) insight
-- has already been sent, so the 30-minute per-user-timezone sweep doesn't
-- re-send it on every pass through the user's push hour window.
alter table daily_insights add column pushed_at timestamptz;
