package com.numerology.util

import com.numerology.db.DatabaseFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/** Small helpers around raw JDBC to avoid boilerplate without pulling in a full ORM. */

fun <T> withConnection(block: (Connection) -> T): T =
    DatabaseFactory.dataSource.connection.use { conn -> block(conn) }

fun <T> Connection.query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): List<T> {
    prepareStatement(sql).use { ps ->
        bindParams(ps, params)
        ps.executeQuery().use { rs ->
            val results = mutableListOf<T>()
            while (rs.next()) results.add(mapper(rs))
            return results
        }
    }
}

fun <T> Connection.queryOne(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): T? =
    query(sql, *params, mapper = mapper).firstOrNull()

fun Connection.update(sql: String, vararg params: Any?): Int {
    prepareStatement(sql).use { ps ->
        bindParams(ps, params)
        return ps.executeUpdate()
    }
}

private fun bindParams(ps: PreparedStatement, params: Array<out Any?>) {
    params.forEachIndexed { idx, value ->
        val i = idx + 1
        when (value) {
            null -> ps.setObject(i, null)
            is java.util.UUID -> ps.setObject(i, value)
            is java.time.LocalDate -> ps.setObject(i, value)
            else -> ps.setObject(i, value)
        }
    }
}
