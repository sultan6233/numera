package com.numerology.repositories

import com.numerology.db.DatabaseFactory.dbQuery
import com.numerology.util.pgJsonb
import com.numerology.util.query
import com.numerology.util.update
import com.numerology.util.withConnection

data class RemoteConfigEntry(val key: String, val valueJson: String, val version: Int)

class RemoteConfigRepository {

    suspend fun getAll(): List<RemoteConfigEntry> = dbQuery {
        withConnection { conn ->
            conn.query("select key, value, version from remote_config") { rs ->
                RemoteConfigEntry(rs.getString("key"), rs.getString("value"), rs.getInt("version"))
            }
        }
    }

    suspend fun upsert(key: String, valueJson: String) = dbQuery {
        withConnection { conn ->
            conn.update(
                """
                insert into remote_config (key, value, version, updated_at)
                values (?, ?, 1, now())
                on conflict (key) do update set
                    value = excluded.value,
                    version = remote_config.version + 1,
                    updated_at = now()
                """.trimIndent(),
                key, pgJsonb(valueJson)
            )
        }
    }
}
