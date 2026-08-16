package com.numerology.repositories

import com.numerology.db.DatabaseFactory.dbQuery
import com.numerology.util.query
import com.numerology.util.update
import com.numerology.util.withConnection
import java.util.UUID

data class PushTokenRecord(val userId: UUID, val platform: String, val token: String)

class PushTokenRepository {

    suspend fun register(userId: UUID, platform: String, token: String) = dbQuery {
        withConnection { conn ->
            conn.update(
                """
                insert into push_tokens (user_id, platform, token, updated_at)
                values (?, ?, ?, now())
                on conflict (platform, token) do update set user_id = excluded.user_id, updated_at = now()
                """.trimIndent(),
                userId, platform, token
            )
        }
    }

    suspend fun tokensForUser(userId: UUID): List<PushTokenRecord> = dbQuery {
        withConnection { conn ->
            conn.query(
                "select user_id, platform, token from push_tokens where user_id = ?",
                userId
            ) { rs ->
                PushTokenRecord(rs.getObject("user_id", UUID::class.java), rs.getString("platform"), rs.getString("token"))
            }
        }
    }

    suspend fun tokensForUsers(userIds: List<UUID>): Map<UUID, List<PushTokenRecord>> = dbQuery {
        if (userIds.isEmpty()) return@dbQuery emptyMap()
        withConnection { conn ->
            val placeholders = userIds.joinToString(",") { "?" }
            conn.query(
                "select user_id, platform, token from push_tokens where user_id in ($placeholders)",
                *userIds.toTypedArray()
            ) { rs ->
                PushTokenRecord(rs.getObject("user_id", UUID::class.java), rs.getString("platform"), rs.getString("token"))
            }.groupBy { it.userId }
        }
    }

    suspend fun deleteToken(token: String) = dbQuery {
        withConnection { conn -> conn.update("delete from push_tokens where token = ?", token) }
    }

    suspend fun allUserIdsWithTokens(): List<UUID> = dbQuery {
        withConnection { conn ->
            conn.query("select distinct user_id from push_tokens") { rs -> rs.getObject("user_id", UUID::class.java) }
        }
    }
}
