package com.numerology.repositories

import com.numerology.db.DatabaseFactory.dbQuery
import com.numerology.security.EncryptionService
import com.numerology.util.query
import com.numerology.util.queryOne
import com.numerology.util.update
import com.numerology.util.withConnection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class UserRecord(
    val id: UUID,
    val deviceId: String,
    val name: String?,
    val birthDate: LocalDate?,
    val language: String,
    val timezone: String?,
    val createdAt: OffsetDateTime,
)

class UserRepository(private val encryption: EncryptionService) {

    suspend fun findByDeviceId(deviceId: String): UserRecord? = dbQuery {
        withConnection { conn ->
            conn.queryOne(
                "select id, device_id, name, birth_date_enc, language, timezone, created_at from users where device_id = ?",
                deviceId
            ) { rs -> rs.toUserRecord() }
        }
    }

    suspend fun findById(id: UUID): UserRecord? = dbQuery {
        withConnection { conn ->
            conn.queryOne(
                "select id, device_id, name, birth_date_enc, language, timezone, created_at from users where id = ?",
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
                returning id, device_id, name, birth_date_enc, language, timezone, created_at
                """.trimIndent(),
                deviceId
            ) { rs -> rs.toUserRecord() }!!
        }
    }

    suspend fun updateProfile(
        userId: UUID,
        name: String?,
        birthDate: LocalDate?,
        language: String?,
        timezone: String?,
    ): UserRecord? = dbQuery {
        withConnection { conn ->
            val birthDateEnc = birthDate?.let { encryption.encrypt(it.toString()) }
            conn.queryOne(
                """
                update users set
                    name = coalesce(?, name),
                    birth_date_enc = coalesce(?, birth_date_enc),
                    language = coalesce(?, language),
                    timezone = coalesce(?, timezone),
                    updated_at = now()
                where id = ?
                returning id, device_id, name, birth_date_enc, language, timezone, created_at
                """.trimIndent(),
                name, birthDateEnc, language, timezone, userId
            ) { rs -> rs.toUserRecord() }
        }
    }

    /** All users that currently have a usable birth date + at least one push-relevant reason to generate content. */
    suspend fun findAllWithBirthDate(): List<UserRecord> = dbQuery {
        withConnection { conn ->
            conn.query(
                "select id, device_id, name, birth_date_enc, language, timezone, created_at from users where birth_date_enc is not null"
            ) { rs -> rs.toUserRecord() }
        }
    }

    private fun java.sql.ResultSet.toUserRecord(): UserRecord {
        val encBirthDate = getString("birth_date_enc")
        return UserRecord(
            id = getObject("id", UUID::class.java),
            deviceId = getString("device_id"),
            name = getString("name"),
            birthDate = encBirthDate?.let { LocalDate.parse(encryption.decrypt(it)) },
            language = getString("language"),
            timezone = getString("timezone"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
        )
    }
}
