package com.numerology.repositories

import com.numerology.db.DatabaseFactory.dbQuery
import com.numerology.util.queryOne
import com.numerology.util.withConnection
import java.util.UUID

data class ComputedNumbersRecord(
    val userId: UUID,
    val lifePath: Int?,
    val expression: Int?,
    val soulUrge: Int?,
    val personality: Int?,
    val birthDay: Int?,
    val healthCode: Int?,
    val businessCode: Int?,
)

class ComputedNumbersRepository {

    suspend fun findByUserId(userId: UUID): ComputedNumbersRecord? = dbQuery {
        withConnection { conn ->
            conn.queryOne(
                """
                select user_id, life_path, expression, soul_urge, personality, birth_day, health_code, business_code
                from computed_numbers where user_id = ?
                """.trimIndent(),
                userId
            ) { rs -> rs.toRecord() }
        }
    }

    suspend fun upsert(record: ComputedNumbersRecord): ComputedNumbersRecord = dbQuery {
        withConnection { conn ->
            conn.queryOne(
                """
                insert into computed_numbers (user_id, life_path, expression, soul_urge, personality, birth_day, health_code, business_code, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, now())
                on conflict (user_id) do update set
                    life_path = coalesce(excluded.life_path, computed_numbers.life_path),
                    expression = coalesce(excluded.expression, computed_numbers.expression),
                    soul_urge = coalesce(excluded.soul_urge, computed_numbers.soul_urge),
                    personality = coalesce(excluded.personality, computed_numbers.personality),
                    birth_day = coalesce(excluded.birth_day, computed_numbers.birth_day),
                    health_code = coalesce(excluded.health_code, computed_numbers.health_code),
                    business_code = coalesce(excluded.business_code, computed_numbers.business_code),
                    updated_at = now()
                returning user_id, life_path, expression, soul_urge, personality, birth_day, health_code, business_code
                """.trimIndent(),
                record.userId, record.lifePath, record.expression, record.soulUrge,
                record.personality, record.birthDay, record.healthCode, record.businessCode,
            ) { rs -> rs.toRecord() }!!
        }
    }

    private fun java.sql.ResultSet.toRecord(): ComputedNumbersRecord = ComputedNumbersRecord(
        userId = getObject("user_id", UUID::class.java),
        lifePath = getObject("life_path") as? Int,
        expression = getObject("expression") as? Int,
        soulUrge = getObject("soul_urge") as? Int,
        personality = getObject("personality") as? Int,
        birthDay = getObject("birth_day") as? Int,
        healthCode = getObject("health_code") as? Int,
        businessCode = getObject("business_code") as? Int,
    )
}
