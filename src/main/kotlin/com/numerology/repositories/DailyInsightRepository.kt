package com.numerology.repositories

import com.numerology.db.DatabaseFactory.dbQuery
import com.numerology.util.pgJsonb
import com.numerology.util.query
import com.numerology.util.queryOne
import com.numerology.util.update
import com.numerology.util.withConnection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class DailyInsightRecord(
    val id: UUID,
    val userId: UUID,
    val date: LocalDate,
    val personalDayNumber: Int,
    val focusArea: String,
    val headline: String,
    val greeting: String?,
    val body: List<String>,
    val suggestedAction: String?,
    val affirmation: String?,
    val luckyNumber: Int?,
    val source: String,
    val createdAt: OffsetDateTime,
    val pushedAt: OffsetDateTime?,
)

data class RecentInsightTitle(val date: LocalDate, val headline: String, val focusArea: String)

class DailyInsightRepository {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findByUserAndDate(userId: UUID, date: LocalDate): DailyInsightRecord? = dbQuery {
        withConnection { conn ->
            conn.queryOne(selectSql("where user_id = ? and date = ?"), userId, date) { rs -> rs.toRecord() }
        }
    }

    suspend fun findByDate(date: LocalDate): List<DailyInsightRecord> = dbQuery {
        withConnection { conn ->
            conn.query(selectSql("where date = ?"), date) { rs -> rs.toRecord() }
        }
    }

    suspend fun recentTitles(userId: UUID, beforeDate: LocalDate, limit: Int = 5): List<RecentInsightTitle> = dbQuery {
        withConnection { conn ->
            conn.query(
                """
                select date, headline, focus_area from daily_insights
                where user_id = ? and date < ?
                order by date desc
                limit ?
                """.trimIndent(),
                userId, beforeDate, limit
            ) { rs ->
                RecentInsightTitle(
                    date = rs.getObject("date", LocalDate::class.java),
                    headline = rs.getString("headline"),
                    focusArea = rs.getString("focus_area"),
                )
            }
        }
    }

    suspend fun upsert(
        userId: UUID,
        date: LocalDate,
        personalDayNumber: Int,
        focusArea: String,
        headline: String,
        greeting: String?,
        body: List<String>,
        suggestedAction: String?,
        affirmation: String?,
        luckyNumber: Int?,
        source: String,
    ): DailyInsightRecord = dbQuery {
        withConnection { conn ->
            conn.queryOne(
                """
                insert into daily_insights
                    (user_id, date, personal_day_number, focus_area, headline, greeting, body, suggested_action, affirmation, lucky_number, source)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (user_id, date) do update set
                    personal_day_number = excluded.personal_day_number,
                    focus_area = excluded.focus_area,
                    headline = excluded.headline,
                    greeting = excluded.greeting,
                    body = excluded.body,
                    suggested_action = excluded.suggested_action,
                    affirmation = excluded.affirmation,
                    lucky_number = excluded.lucky_number,
                    source = excluded.source
                returning ${columns()}
                """.trimIndent(),
                userId, date, personalDayNumber, focusArea, headline, greeting,
                pgJsonb(json.encodeToString(body)), suggestedAction, affirmation, luckyNumber, source,
            ) { rs -> rs.toRecord() }!!
        }
    }

    suspend fun markPushed(userId: UUID, date: LocalDate) = dbQuery {
        withConnection { conn ->
            conn.update(
                "update daily_insights set pushed_at = now() where user_id = ? and date = ?",
                userId, date
            )
        }
    }

    private fun columns() = "id, user_id, date, personal_day_number, focus_area, headline, greeting, body, suggested_action, affirmation, lucky_number, source, created_at, pushed_at"
    private fun selectSql(whereClause: String) = "select ${columns()} from daily_insights $whereClause"

    private fun java.sql.ResultSet.toRecord(): DailyInsightRecord = DailyInsightRecord(
        id = getObject("id", UUID::class.java),
        userId = getObject("user_id", UUID::class.java),
        date = getObject("date", LocalDate::class.java),
        personalDayNumber = getInt("personal_day_number"),
        focusArea = getString("focus_area"),
        headline = getString("headline"),
        greeting = getString("greeting"),
        body = json.decodeFromString(getString("body")),
        suggestedAction = getString("suggested_action"),
        affirmation = getString("affirmation"),
        luckyNumber = (getObject("lucky_number") as? Int),
        source = getString("source"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        pushedAt = getObject("pushed_at", OffsetDateTime::class.java),
    )
}
