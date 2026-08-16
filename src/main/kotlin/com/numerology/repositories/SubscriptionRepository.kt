package com.numerology.repositories

import com.numerology.db.DatabaseFactory.dbQuery
import com.numerology.util.pgJsonb
import com.numerology.util.query
import com.numerology.util.queryOne
import com.numerology.util.update
import com.numerology.util.withConnection
import java.time.OffsetDateTime
import java.util.UUID

data class SubscriptionRecord(
    val id: UUID,
    val userId: UUID?,
    val platform: String,
    val productId: String?,
    val status: String,
    val expiresAt: OffsetDateTime?,
    val originalTransactionId: String,
)

class SubscriptionRepository {

    /** Idempotent upsert keyed by (platform, original_transaction_id) — safe against duplicate webhook deliveries. */
    suspend fun upsert(
        userId: UUID?,
        platform: String,
        productId: String?,
        status: String,
        expiresAt: OffsetDateTime?,
        originalTransactionId: String,
        latestTransactionId: String?,
        rawPayloadJson: String,
    ): SubscriptionRecord = dbQuery {
        withConnection { conn ->
            conn.queryOne(
                """
                insert into subscriptions
                    (user_id, platform, product_id, status, expires_at, original_transaction_id, latest_transaction_id, raw_payload, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, now())
                on conflict (platform, original_transaction_id) do update set
                    user_id = coalesce(excluded.user_id, subscriptions.user_id),
                    product_id = coalesce(excluded.product_id, subscriptions.product_id),
                    status = excluded.status,
                    expires_at = excluded.expires_at,
                    latest_transaction_id = coalesce(excluded.latest_transaction_id, subscriptions.latest_transaction_id),
                    raw_payload = excluded.raw_payload,
                    updated_at = now()
                returning id, user_id, platform, product_id, status, expires_at, original_transaction_id
                """.trimIndent(),
                userId, platform, productId, status, expiresAt, originalTransactionId,
                latestTransactionId, pgJsonb(rawPayloadJson),
            ) { rs -> rs.toRecord() }!!
        }
    }

    suspend fun findActiveForUser(userId: UUID): List<SubscriptionRecord> = dbQuery {
        withConnection { conn ->
            conn.query(
                """
                select id, user_id, platform, product_id, status, expires_at, original_transaction_id
                from subscriptions
                where user_id = ? and status in ('active', 'grace_period')
                  and (expires_at is null or expires_at > now())
                order by expires_at desc nulls last
                """.trimIndent(),
                userId
            ) { rs -> rs.toRecord() }
        }
    }

    suspend fun linkUserByOriginalTransactionId(platform: String, originalTransactionId: String, userId: UUID): Int = dbQuery {
        withConnection { conn ->
            conn.update(
                "update subscriptions set user_id = ? where platform = ? and original_transaction_id = ? and user_id is null",
                userId, platform, originalTransactionId
            )
        }
    }

    private fun java.sql.ResultSet.toRecord(): SubscriptionRecord = SubscriptionRecord(
        id = getObject("id", UUID::class.java),
        userId = getObject("user_id") as? UUID,
        platform = getString("platform"),
        productId = getString("product_id"),
        status = getString("status"),
        expiresAt = getObject("expires_at", OffsetDateTime::class.java),
        originalTransactionId = getString("original_transaction_id"),
    )
}
