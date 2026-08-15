package com.numerology.repositories

import com.numerology.db.DatabaseFactory.dbQuery
import com.numerology.util.update
import com.numerology.util.withConnection

/** Idempotency ledger: Apple/Google may redeliver the same notification multiple times. */
class WebhookEventRepository {

    /** Returns true if this event was newly recorded (i.e. not a duplicate). */
    suspend fun tryRecord(platform: String, eventId: String): Boolean = dbQuery {
        withConnection { conn ->
            val rows = conn.update(
                "insert into webhook_events (platform, event_id) values (?, ?) on conflict (platform, event_id) do nothing",
                platform, eventId
            )
            rows > 0
        }
    }
}
