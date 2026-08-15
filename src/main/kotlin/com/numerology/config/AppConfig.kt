package com.numerology.config

/**
 * All configuration comes from environment variables so secrets never live in
 * source control or the client app. See .env.example for the full list and
 * where to obtain each value.
 */
data class AppConfig(
    val port: Int,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val encryptionKeyBase64: String,
    val openAiApiKey: String?,
    val openAiModel: String,
    val openAiBaseUrl: String,
    val openAiTimeoutMs: Long,
    val nightlyBatchHour: Int,
    val nightlyBatchMinute: Int,
    val dailyPushHour: Int,
    val dailyPushMinute: Int,
    val schedulerTimezone: String,
    val googleServiceAccountJsonPath: String?,
    val googlePlayPackageName: String?,
    val googlePubSubSubscription: String?,
    val googleCloudProjectId: String?,
    val firebaseServiceAccountJsonPath: String?,
    val applePrivateKeyPath: String?,
    val appleKeyId: String?,
    val appleIssuerId: String?,
    val appleBundleId: String?,
    val adminToken: String,
) {
    companion object {
        fun fromEnv(): AppConfig {
            fun env(name: String, default: String? = null): String =
                System.getenv(name) ?: default
                ?: error("Missing required environment variable: $name")

            fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

            return AppConfig(
                port = env("PORT", "8080").toInt(),
                dbUrl = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/numerology"),
                dbUser = env("DATABASE_USER", "numerology"),
                dbPassword = env("DATABASE_PASSWORD", "numerology"),
                jwtSecret = env("JWT_SECRET"),
                jwtIssuer = env("JWT_ISSUER", "numerology-backend"),
                jwtAudience = env("JWT_AUDIENCE", "numerology-app"),
                encryptionKeyBase64 = env("ENCRYPTION_KEY"),
                openAiApiKey = envOrNull("OPENAI_API_KEY"),
                openAiModel = env("OPENAI_MODEL", "gpt-4o-mini"),
                openAiBaseUrl = env("OPENAI_BASE_URL", "https://api.openai.com/v1"),
                openAiTimeoutMs = env("OPENAI_TIMEOUT_MS", "12000").toLong(),
                nightlyBatchHour = env("NIGHTLY_BATCH_HOUR", "3").toInt(),
                nightlyBatchMinute = env("NIGHTLY_BATCH_MINUTE", "0").toInt(),
                dailyPushHour = env("DAILY_PUSH_HOUR", "9").toInt(),
                dailyPushMinute = env("DAILY_PUSH_MINUTE", "0").toInt(),
                schedulerTimezone = env("SCHEDULER_TIMEZONE", "Asia/Tashkent"),
                googleServiceAccountJsonPath = envOrNull("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"),
                googlePlayPackageName = envOrNull("GOOGLE_PLAY_PACKAGE_NAME"),
                googlePubSubSubscription = envOrNull("GOOGLE_PUBSUB_SUBSCRIPTION"),
                googleCloudProjectId = envOrNull("GOOGLE_CLOUD_PROJECT_ID"),
                firebaseServiceAccountJsonPath = envOrNull("FIREBASE_SERVICE_ACCOUNT_JSON"),
                applePrivateKeyPath = envOrNull("APPLE_PRIVATE_KEY_PATH"),
                appleKeyId = envOrNull("APPLE_KEY_ID"),
                appleIssuerId = envOrNull("APPLE_ISSUER_ID"),
                appleBundleId = envOrNull("APPLE_BUNDLE_ID"),
                adminToken = env("ADMIN_TOKEN", "change-me"),
            )
        }
    }
}
