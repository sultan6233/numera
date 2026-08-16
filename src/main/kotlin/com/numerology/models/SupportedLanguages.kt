package com.numerology.models

/**
 * App-supported content languages. Codes are what clients send (profile
 * `language` field, X-Language header) and what's stored in `users.language`;
 * display names are what gets dropped into the LLM prompt so the model
 * writes its response in the right language regardless of what language the
 * prompt/instructions themselves are written in.
 */
object SupportedLanguages {
    private val displayNames = mapOf(
        "en" to "English",
        "es" to "Spanish",
        "pt-BR" to "Portuguese (Brazil)",
        "uk" to "Ukrainian",
        "tr" to "Turkish",
        "de" to "German",
        "fr" to "French",
        "pl" to "Polish",
        "it" to "Italian",
        "ru" to "Russian",
    )

    const val DEFAULT: String = "ru"

    /** All supported codes, e.g. for FallbackBank to know which resource files to look for. */
    val CODES: Set<String> = displayNames.keys

    /** Used when a user has no name on file yet — fed to the LLM as data, not the prompt's own language. */
    private val defaultUserNames = mapOf(
        "en" to "friend",
        "es" to "amigo/a",
        "pt-BR" to "amigo(a)",
        "uk" to "друже",
        "tr" to "dostum",
        "de" to "Freund/in",
        "fr" to "ami(e)",
        "pl" to "przyjacielu",
        "it" to "amico/a",
        "ru" to "друг",
    )

    fun isSupported(code: String): Boolean = displayNames.containsKey(code)

    /** English display name for the given code, for embedding in an LLM prompt; falls back to Russian. */
    fun displayName(code: String?): String = displayNames[code] ?: displayNames.getValue(DEFAULT)

    fun defaultUserName(code: String?): String = defaultUserNames[code] ?: defaultUserNames.getValue(DEFAULT)
}
