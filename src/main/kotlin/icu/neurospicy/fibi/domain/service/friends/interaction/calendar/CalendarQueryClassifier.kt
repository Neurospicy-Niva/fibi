package icu.neurospicy.fibi.domain.service.friends.interaction.calendar

import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId

@Component
class CalendarQueryClassifier(
    private val llmClient: LlmClient,
    private val complexTaskModel: String,
) {
    suspend fun classify(
        message: String, timezone: ZoneId, receivedAt: Instant,
    ): ParsedCalendarQuery {
        if (message.isBlank()) return ParsedCalendarQuery(CalendarQueryCategory.NoSearch)
        val prompt = """
You are a smart classifier for calendar queries. Your task is to classify the user's request into one of the following categories:

- SpecificTimeRange: A clearly defined time interval (e.g., "between June 10 and June 12", "on March 3rd")
- RelativeTimeRange: A vague or relative range (e.g., "appointments next week", "in 5 days", "last month")
- KeywordSearch: The user is looking for appointments based on topic, person or type (e.g., "all meetings", "appointments with Jane", "doctor visits")
- KeywordInSpecificTimeRange: A combination of KeywordSearch and SpecificTimeRange
- KeywordInRelativeTimeRange: A combination of KeywordSearch and RelativeTimeRange
- CombinedQuery: Any other query 
- NoSearch: The user input is unclear or no relevant calendar request is expressed

Return just the category. No chat, no explanation.

User query:
"$message"
""".trimIndent()
        val json = llmClient.promptReceivingText(
            listOf(UserMessage(prompt)),
            OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.8).build(),
            timezone,
            receivedAt
        ) ?: return ParsedCalendarQuery(category = CalendarQueryCategory.KeywordSearch)
        return ParsedCalendarQuery(category = CalendarQueryCategory.entries.firstOrNull { it.name.lowercase() == json.lowercase() }
            ?: CalendarQueryCategory.NoSearch)
    }
}

data class ParsedCalendarQuery(
    val category: CalendarQueryCategory, val keywords: List<String> = emptyList(), val timeHint: String? = null,
)

enum class CalendarQueryCategory {
    SpecificTimeRange, RelativeTimeRange, KeywordInSpecificTimeRange, KeywordInRelativeTimeRange, CombinedQuery, KeywordSearch, NoSearch
}