package com.numerology.repositories

import com.numerology.db.DatabaseFactory.dbQuery
import com.numerology.security.EncryptionService
import com.numerology.util.query
import com.numerology.util.queryOne
import com.numerology.util.withConnection
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

data class UserRecord(
    val id: UUID,
    val deviceId: String,
    val name: String?,
    val birthDate: LocalDate?,
    val language: String,
    val timezone: String?,
    val timezoneOffsetMinutes: Int?,
    val createdAt: OffsetDateTime,
)

class UserRepository(private val encryption: EncryptionService) {

    suspend fun findByDeviceId(deviceId: String): UserRecord? = dbQuery {
        withConnection { conn ->
            conn.queryOne(
                "select ${columns()} from users where device_id = ?",
                deviceId
            ) { rs -> rs.toUserRecord() }
        }
    }

    suspend fun findById(id: UUID): UserRecord? = dbQuery {
        withConnection { conn ->
            conn.queryOne(
                "select ${columns()} from users where id = ?",
                id
            ) { rs -> rs.toUserRecord() }
        }
    }

    suspend fun createAnonymous(deviceId: String): UserRecord = dbQuery {
        withConnection { conn ->
            conn.queryOne(
                """
                insert into users (device_id) values (?)
                on conflict (device_id) do update set device_id = excluded.device_id
                returning ${columns()}
                """.trimIndent(),
                deviceId
            ) { rs -> rs.toUserRecord() }!!
        }
    }

    /**
     * `timezone` drives everything display/Kotlin-side (see resolveUserZone);
     * `timezone_offset_minutes` is derived from it here, once, via java.time
     * (see the V4 migration comment for why this must not be recomputed in
     * SQL from the raw string) and used only by the scheduler's SQL queries.
     */
    suspend fun updateProfile(
        userId: UUID,
        name: String?,
        birthDate: LocalDate?,
        language: String?,
        timezone: String?,
    ): UserRecord? = dbQuery {
        val timezoneOffsetMinutes = timezone?.let { tz ->
            runCatching { ZoneId.of(tz).rules.getOffset(Instant.now()).totalSeconds / 60 }.getOrNull()
        }
        withConnection { conn ->
            val birthDateEnc = birthDate?.let { encryption.encrypt(it.toString()) }
            conn.queryOne(
                """
                update users set
                    name = coalesce(?, name),
                    birth_date_enc = coalesce(?, birth_date_enc),
                    language = coalesce(?, language),
                    timezone = coalesce(?, timezone),
                    timezone_offset_minutes = coalesce(?, timezone_offset_minutes),
                    updated_at = now()
                where id = ?
                returning ${columns()}
                """.trimIndent(),
                name, birthDateEnc, language, timezone, timezoneOffsetMinutes, userId
            ) { rs -> rs.toUserRecord() }
        }
    }

    /** All users that currently have a usable birth date + at least one push-relevant reason to generate content. */
    suspend fun findAllWithBirthDate(): List<UserRecord> = dbQuery {
        withConnection { conn ->
            conn.query(
                "select ${columns()} from users where birth_date_enc is not null"
            ) { rs -> rs.toUserRecord() }
        }
    }

    /**
     * Active subscribers whose local clock (via timezone_offset_minutes,
     * falling back to defaultOffsetMinutes) is at targetHour right now, and
     * who don't already have a daily_insight for their local tomorrow.
     * Pure interval arithmetic -- no AT TIME ZONE -- so this is called once
     * per sweep tick instead of loading every subscriber into Kotlin to
     * check each one there.
     */
    suspend fun findActiveSubscribersDueForInsight(defaultOffsetMinutes: Int, targetHour: Int): List<UserRecord> = dbQuery {
        withConnection { conn ->
            conn.query(
                """
                with candidates as (
                    select
                        u.id, u.device_id, u.name, u.birth_date_enc, u.language, u.timezone, u.timezone_offset_minutes, u.created_at,
                        (now() + (coalesce(u.timezone_offset_minutes, ?) || ' minutes')::interval) as local_now
                    from users u
                    join subscriptions s on s.user_id = u.id
                    where s.status in ('active', 'grace_period')
                      and (s.expires_at is null or s.expires_at > now())
                      and u.birth_date_enc is not null
                )
                select id, device_id, name, birth_date_enc, language, timezone, timezone_offset_minutes, created_at
                from candidates c
                where extract(hour from c.local_now) = ?
                  and not exists (
                      select 1 from daily_insights di
                      where di.user_id = c.id and di.date = (c.local_now::date + 1)
                  )
                """.trimIndent(),
                defaultOffsetMinutes, targetHour
            ) { rs -> rs.toUserRecord() }
        }
    }

    private fun columns() = "id, device_id, name, birth_date_enc, language, timezone, timezone_offset_minutes, created_at"

    private fun java.sql.ResultSet.toUserRecord(): UserRecord {
        val encBirthDate = getString("birth_date_enc")
        return UserRecord(
            id = getObject("id", UUID::class.java),
            deviceId = getString("device_id"),
            name = getString("name"),
            birthDate = encBirthDate?.let { LocalDate.parse(encryption.decrypt(it)) },
            language = getString("language"),
            timezone = getString("timezone"),
            timezoneOffsetMinutes = (getObject("timezone_offset_minutes") as? Int),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
        )
    }
}
