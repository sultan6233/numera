package com.numerology.db

import com.numerology.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import javax.sql.DataSource

object DatabaseFactory {
    lateinit var dataSource: DataSource
        private set

    fun init(config: AppConfig) {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            isAutoCommit = true
        }
        dataSource = HikariDataSource(hikariConfig)

        Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:/app/db/migration")
            .validateMigrationNaming(true)
            .load()
            .migrate()
    }

    /** Run a blocking JDBC block off the main dispatcher. */
    suspend fun <T> dbQuery(block: () -> T): T =
        withContext(Dispatchers.IO) { block() }
}
