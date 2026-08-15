package com.numerology.repositories

import com.numerology.db.DatabaseFactory.dbQuery
import com.numerology.security.EncryptionService
import com.numerology.util.query
import com.numerology.util.queryOne
import com.numerology.util.update
import com.numerology.util.withConnection
import java.time.LocalDate
import java.util.UUID

data class CompanionRecord(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val birthDate: LocalDate,
    val relationLabel: String?,
)

class CompanionRepository(private val encryption: EncryptionService) {

    suspend fun listForUser(userId: UUID): List<CompanionRecord> = dbQuery {
        withConnection { conn ->
            conn.query(
                "select id, user_id, name, birth_date_enc, relation_label from companions where user_id = ? order by created_at",
                userId
            ) { rs -> rs.toCompanionRecord() }
        }
    }

    suspend fun create(userId: UUID, name: String, birthDate: LocalDate, relationLabel: String?): CompanionRecord = dbQuery {
        withConnection { conn ->
            conn.queryOne(
                """
                insert into companions (user_id, name, birth_date_enc, relation_label)
                values (?, ?, ?, ?)
                returning id, user_id, name, birth_date_enc, relation_label
                """.trimIndent(),
                userId, name, encryption.encrypt(birthDate.toString()), relationLabel
            ) { rs -> rs.toCompanionRecord() }!!
        }
    }

    suspend fun delete(userId: UUID, companionId: UUID): Boolean = dbQuery {
        withConnection { conn ->
            conn.update("delete from companions where id = ? and user_id = ?", companionId, userId) > 0
        }
    }

    private fun java.sql.ResultSet.toCompanionRecord(): CompanionRecord = CompanionRecord(
        id = getObject("id", UUID::class.java),
        userId = getObject("user_id", UUID::class.java),
        name = getString("name"),
        birthDate = LocalDate.parse(encryption.decrypt(getString("birth_date_enc"))),
        relationLabel = getString("relation_label"),
    )
}
