package com.numerology.llm

import com.numerology.config.AppConfig
import com.numerology.models.LlmInsightPayload
import com.numerology.models.SupportedLanguages
import com.numerology.numerology.FocusArea
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("OpenAiClient")

/** Everything the LLM needs to write today's insight for one user. */
data class InsightGenerationContext(
    val userName: String,
    val lifePathNumber: Int?,
    val expressionNumber: Int?,
    val soulUrgeNumber: Int?,
    val personalityNumber: Int?,
    val todayDate: String,
    val personalDayNumber: Int,
    val focusTheme: String,
    val recentTitles: List<String>, // "date — headline (focus_area)" lines, most recent first
    val language: String, // code from SupportedLanguages, e.g. "ru", "en", "pt-BR" — see buildSystemPrompt
)

private const val SYSTEM_PROMPT = """
Ты — опытный нумеролог и цифровой психолог, который пишет короткие персональные
ежедневные разборы для пользователей мобильного приложения по системе
пифагорейской нумерологии.

Твоя задача — на основе чисел пользователя и текущего Личного Дня (Personal Day
Number) сгенерировать тёплый, конкретный, немного мистический, но приземлённый
инсайт на сегодня.

ПРАВИЛА:

1. Пиши от первого лица к пользователю ("ты"), тёплым доверительным тоном — как
   личный наставник, а не как бот и не как гороскоп из газеты.

2. НИКОГДА не давай гарантированных предсказаний будущего ("сегодня точно
   произойдёт X", "ты обязательно встретишь..."). Формулируй как энергию,
   вероятность, подсказку: "сегодня благоприятный фон для...", "обрати внимание
   на...", "энергия дня располагает к...".

3. НИКОГДА не давай медицинских, юридических или финансовых советов как факт.
   Про здоровье/деньги можно говорить только в ключе общей энергии дня, не как
   рекомендацию к конкретному действию с деньгами или здоровьем
   (запрещено: "инвестируй в X", "у тебя проблема со здоровьем", "не ходи к врачу").

4. Не повторяй формулировки, метафоры и темы из недавних дней (список ниже). Если
   focus_area похож на вчерашний — заходи с другого угла.

5. Коротко и без воды: headline — до 8 слов. Тело — 2-3 абзаца по 40-70 слов
   каждый. Без штампов уровня "вселенная приготовила для тебя подарок" и без
   generic-фраз, которые подошли бы любому человеку с любыми числами.

6. Обязательно используй конкретные числа пользователя в тексте (не абстрактно
   "твоя энергия", а "твоё число души — 7, поэтому сегодня...").

7. Заверши мягким предложением действия на сегодня (не императив "ты должен", а
   предложение) и короткой аффирмацией от первого лица пользователя ("я...").

8. Отвечай СТРОГО в формате JSON по схеме ниже. Никакого текста вне JSON, никакого
   markdown-форматирования внутри значений.
"""

/**
 * These rules above are authored once, in Russian — LLMs follow "write your
 * output in language X" instructions reliably even when the surrounding
 * instructions are in a different language, so there's no need to maintain
 * a parallel translated prompt per SupportedLanguages entry. Only the final
 * output language changes; JSON keys stay in English (buildUserPrompt's
 * schema already says so).
 */
private fun buildSystemPrompt(languageName: String): String =
    SYSTEM_PROMPT.trimIndent() + "\n\n9. Весь текст в значениях JSON (headline, greeting, body, " +
        "suggested_action, affirmation) пиши ПОЛНОСТЬЮ на языке: $languageName — включая " +
        "обращения, грамматический род и идиомы, естественные для носителя этого языка. " +
        "Названия ключей JSON оставляй на английском, как в схеме."

private fun buildUserPrompt(ctx: InsightGenerationContext): String {
    val recentBlock = if (ctx.recentTitles.isEmpty()) "(нет данных за последние дни)" else ctx.recentTitles.joinToString("\n")
    return """
Профиль пользователя:
- Имя: ${ctx.userName}
- Число жизненного пути: ${ctx.lifePathNumber?.toString() ?: "неизвестно"}
- Число судьбы/экспрессии: ${ctx.expressionNumber?.toString() ?: "неизвестно"}
- Число души: ${ctx.soulUrgeNumber?.toString() ?: "неизвестно"}
- Число личности: ${ctx.personalityNumber?.toString() ?: "неизвестно"}

Текущий день:
- Дата: ${ctx.todayDate}
- Личный День (Personal Day Number): ${ctx.personalDayNumber}
- Фокус-тема на сегодня: ${ctx.focusTheme}
  (одно из, на языке ответа: ${FocusArea.allLabelsFor(ctx.language).joinToString(" | ")})

Заголовки/темы последних 5 дней (НЕ повторять ракурс и формулировки):
$recentBlock

Язык ответа: ${SupportedLanguages.displayName(ctx.language)} (весь текст в значениях JSON).

Сгенерируй инсайт на сегодня строго в этом формате JSON:

{
  "headline": "string, до 8 слов, зацепляющий для push-уведомления",
  "greeting": "string, 1 короткая фраза-обращение по имени",
  "body": ["абзац 1", "абзац 2", "абзац 3 (опционально)"],
  "focus_area": "string, одно из значений фокус-темы выше",
  "suggested_action": "string, мягкое предложение действия на сегодня, 1 предложение",
  "affirmation": "string, от первого лица пользователя, 1 предложение",
  "lucky_number": число 1-9
}
""".trimIndent()
}

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ResponseFormat(val type: String = "json_object")

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.9,
    @SerialName("response_format") val responseFormat: ResponseFormat = ResponseFormat(),
)

@Serializable
private data class ChatChoice(val message: ChatMessage)

@Serializable
private data class ChatCompletionResponse(val choices: List<ChatChoice>)

class OpenAiClient(private val config: AppConfig) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = config.openAiTimeoutMs
            connectTimeoutMillis = config.openAiTimeoutMs
        }
    }

    suspend fun generateInsight(ctx: InsightGenerationContext): LlmInsightPayload? {
        val apiKey = config.openAiApiKey
        if (apiKey.isNullOrBlank()) {
            logger.warn("OPENAI_API_KEY not set — skipping LLM call, caller should fall back to the static bank")
            return null
        }
        return try {
            val response = client.post("${config.openAiBaseUrl}/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    ChatCompletionRequest(
                        model = config.openAiModel,
                        messages = listOf(
                            ChatMessage("system", buildSystemPrompt(SupportedLanguages.displayName(ctx.language))),
                            ChatMessage("user", buildUserPrompt(ctx)),
                        ),
                    )
                )
            }
            if (!response.status.isSuccess()) {
                logger.warn("OpenAI call failed: HTTP {} — {}", response.status, response.bodyAsText())
                return null
            }
            val parsed: ChatCompletionResponse = response.body()
            val content = parsed.choices.firstOrNull()?.message?.content ?: return null
            json.decodeFromString<LlmInsightPayload>(content)
        } catch (e: Exception) {
            logger.warn("OpenAI call failed / timed out: {}", e.message)
            null
        }
    }
}
