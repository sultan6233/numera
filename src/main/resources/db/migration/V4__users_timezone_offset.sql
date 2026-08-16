-- Precomputed current UTC offset in minutes for the user's `timezone`,
-- kept in sync alongside it (see UserRepository.updateProfile). Computed in
-- Kotlin via java.time.ZoneId at write time -- deliberately NOT derived in
-- SQL via `AT TIME ZONE` on a raw offset string, because Postgres interprets
-- bare signed-offset text (e.g. '+05:00') with the inverted POSIX sign
-- convention (gives UTC-5, not UTC+5), while java.time gets it right. This
-- column lets the scheduler filter "who is due right now" with plain
-- interval arithmetic in one query instead of pulling every candidate user
-- into the app and checking each one in Kotlin.
alter table users add column timezone_offset_minutes int;
