# Numerology backend

Kotlin + Ktor backend for the numerology app, implemented per `numerology_backend_spec.md`.
Numerology arithmetic (life path, expression, soul urge, personality...) stays on the
client as specified; this backend handles AI daily insights, subscriptions, push,
and remote config.

## Stack

- Kotlin + Ktor (Netty engine)
- PostgreSQL via raw JDBC + HikariCP (no ORM — kept deliberately simple/predictable)
- Flyway for migrations
- OpenAI (GPT) for daily insight generation, with a local fallback bank
- Firebase Cloud Messaging for push
- Google Play Developer API + Pub/Sub (pull) for subscription verification
- Docker + docker-compose for deployment

## Running

```
cp .env.example .env
# fill in JWT_SECRET, ENCRYPTION_KEY, ADMIN_TOKEN at minimum (see .env.example for how)
mkdir -p secrets   # drop Google/Firebase/Apple credential files here once you have them
docker compose up -d --build
```

The app runs Flyway migrations automatically on startup. First boot with no
`OPENAI_API_KEY` and no Google/Firebase credentials is intentional and safe: those
integrations just log a warning and no-op (LLM calls fall back to the static bank,
push/Google calls are skipped) so you can smoke-test the rest of the API immediately.

## Endpoints

**Auth / профиль**
- `POST /auth/anonymous` — `{deviceId}` -> `{userId, token}`. Token is a JWT, send as `Authorization: Bearer <token>` on every other call.
- `POST /profile` (auth) — `{name, birthDate, language?, timezone?, computedNumbers?}`. `computedNumbers` is whatever the client already computed (life path, expression, soul urge, personality, ...) — the backend caches it, it does not recompute the numerology itself (per spec §1).
- `GET /profile` (auth) — read back the saved profile.
- `POST /profile/companions`, `GET /profile/companions`, `DELETE /profile/companions/{id}` (auth)

**Optional headers on every authenticated request** (`plugins/Security.kt`) — cheap opportunistic profile sync, no dedicated "update" endpoint needed:
- `X-Timezone: Asia/Tashkent` or `+05:00` (anything `java.time.ZoneId` accepts) — drives when the nightly insight/push jobs fire for this user (`NightlyBatchJob`, `PushService`) and what "today" means in `GET /daily-insight`.
- `X-Language: en|es|pt-BR|uk|tr|de|fr|pl|it|ru` (see `models/SupportedLanguages.kt`) — what language the LLM writes the daily insight in (`llm/OpenAiClient.kt`). Static fallback-bank content (used only when the LLM call fails/isn't configured) is Russian-only for now.

**Контент**
- `GET /daily-insight?date=YYYY-MM-DD` (auth) — cache read; generates synchronously as a fallback if the nightly batch hasn't produced today's entry yet.
- `GET /reference/numbers` — static versioned JSON (`src/main/resources/reference_numbers.json`), redeploy to update, no app release needed.

**Подписки**
- `POST /webhooks/apple`, `POST /webhooks/google` — server-to-server, idempotent (see "Idempotency" below).
- `GET /entitlement` (auth) — `{active, platform, productId, status, expiresAt}`.
- `POST /subscriptions/verify/apple`, `POST /subscriptions/verify/google` (auth) — **addition beyond the original spec**, see "Linking a purchase" below.

**Push**
- `POST /push/register` (auth) — `{platform: "ios"|"android", token}`.
- Internal cron sends push once a day using each user's cached headline.

**Remote config**
- `GET /config` — merged JSON of all `remote_config` rows (paywall variant, feature flags).
- `PUT /admin/config/{key}` with header `X-Admin-Token: <ADMIN_TOKEN>` — **addition beyond the spec**: a curl-able way to edit config without a redeploy, since the spec explicitly leaves the admin CMS to Phase 2.

## Two additions beyond the literal spec, and why

1. **Linking a purchase to a user (`POST /subscriptions/verify/apple|google`).** The
   spec lists only the async webhooks + `GET /entitlement`, but doesn't say how a
   fresh purchase or a "restore purchases" tap ever gets associated with an
   anonymous `user_id` in the first place — Apple/Google webhooks don't carry your
   app's user id. The client calls this right after StoreKit2 / Play Billing confirms
   a transaction, so entitlement is instant instead of waiting for the async webhook.
   The webhook then keeps status fresh going forward (renewals, cancellations,
   refunds) by matching on the same `original_transaction_id` / `purchaseToken`.

2. **`PUT /admin/config/{key}`.** Needed *some* way to actually change remote config
   short of hand-editing Postgres. Deliberately minimal (bearer token, not a UI) since
   a real admin CMS is explicitly Phase 2 in the spec.

## Apple / Google / Firebase credentials — current status

You said: Google Play + Firebase you can provide, Apple can stay stubbed. Concretely:

- **Google Play**: needs `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` (a service account key
  file) + `GOOGLE_PLAY_PACKAGE_NAME` (your Android app id). Exact steps are in
  `.env.example`. Without it, `/subscriptions/verify/google` and the Google webhook
  path will log a warning and return "unknown"/skip — everything else keeps working.
- **Firebase**: needs `FIREBASE_SERVICE_ACCOUNT_JSON` + `GOOGLE_CLOUD_PROJECT_ID`
  (same Firebase project, "Generate new private key" under Project Settings ->
  Service accounts). Without it, push sending is a no-op (logged, not an error).
- **Apple**: `/webhooks/apple` and `/subscriptions/verify/apple` already parse and
  store App Store Server Notifications V2 / StoreKit2 transaction payloads, but
  `AppleJwsDecoder.kt` does **not yet verify the cryptographic signature** — it's
  structurally ready, not yet trustworthy for money-sensitive decisions. When you
  have the App Store Server API key (.p8 + Key ID + Issuer ID) from App Store
  Connect, either wire up signature verification there or swap in Apple's official
  `app-store-server-library` (recommended). Also: Apple requires the notification
  URL to be HTTPS on a real domain, which the server doesn't have yet (IP-only per
  your earlier answer).

## Idempotency

`webhook_events (platform, event_id)` is a unique ledger — Apple's `notificationUUID`
and Google Pub/Sub's `messageId` are recorded there before processing, so redelivered
notifications (both platforms retry aggressively) are detected and skipped.
`subscriptions (platform, original_transaction_id)` is also unique, so even without
the ledger a duplicate webhook would just upsert the same row rather than duplicate it.

## AI generation

`llm/OpenAiClient.kt` embeds the system/user prompts from spec §5 verbatim (Russian,
JSON-only response). `services/FallbackBank.kt` loads `fallback_insights.json`
(5 hand-written entries per Personal Day Number 1-9 = 45 total) and picks one
deterministically per user+day so the fallback doesn't feel obviously repetitive
during an LLM outage. `services/InsightService.kt` tries the LLM first and only
falls back on failure/timeout/missing API key — the user is never shown an error.

The nightly batch (`scheduler/NightlyBatchJob.kt`, default 03:00
`SCHEDULER_TIMEZONE`) generates tomorrow's insight for every user with a currently
active (or grace-period) subscription — this is what keeps LLM spend proportional
to paying users rather than to app opens, per spec §5/§6.

## Sensitive data

`birth_date` is AES-256-GCM encrypted at the application layer before it ever
reaches Postgres (`security/EncryptionService.kt`, column `birth_date_enc`) — set
`ENCRYPTION_KEY` to a real `openssl rand -base64 32` value before going to
production. Logging (`plugins/StatusPagesConfig.kt`) never includes request
bodies, only path + exception type, so birth dates can't leak into logs that way
either.

## What's still open

- Apple JWS signature verification (see above).
- Per-timezone push delivery — the spec's single daily cron sends to everyone at
  the same server-local time; wave-by-timezone is listed as an optional
  improvement in the original spec's hosting note, not required for MVP.
- HTTPS/domain for Apple notifications and Google Pub/Sub *push* (pull works
  without it, see `.env.example`).
