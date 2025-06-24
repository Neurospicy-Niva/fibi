package icu.neurospicy.fibi.domain.service.friends.interaction.calendar

import icu.neurospicy.fibi.BaseAIT
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.ZoneId

class CalendarQueryClassifierAIT : BaseAIT() {

    @Autowired
    lateinit var calendarQueryClassifier: CalendarQueryClassifier

    private val now = Instant.now()
    private val timezone = ZoneId.of("Europe/Berlin")

    @Test
    fun `classifies specific time range`() = runClassify(
        "What do I have between July 1st and July 5th?", CalendarQueryCategory.SpecificTimeRange
    )

    @Test
    fun `classifies relative time range`() = runClassify(
        "What appointments do I have next week?", CalendarQueryCategory.RelativeTimeRange
    )

    @Test
    fun `classifies keyword search`() = runClassify(
        "Show me all meetings with Jane",
        CalendarQueryCategory.KeywordSearch,
    )

    @Test
    fun `classifies keyword in specific time range`() = runClassify(
        "When is my dentist appointment on July 21st",
        CalendarQueryCategory.KeywordInSpecificTimeRange,
    )

    @Test
    fun `classifies keyword in relative time range`() = runClassify(
        "Which dentist appointments do I have next month?",
        CalendarQueryCategory.KeywordInRelativeTimeRange,
    )

    @Test
    fun `classifies combined query`() = runClassify(
        "List today's appointments that are no longer than an hour?",
        CalendarQueryCategory.CombinedQuery,
    )

    @Test
    fun `handles unclear query gracefully`() = runClassify(
        "I'm not sure what I mean", CalendarQueryCategory.NoSearch
    )

    @Test
    fun `handles empty input`() = runClassify(
        "", CalendarQueryCategory.NoSearch
    )

    private fun runClassify(
        text: String,
        expectedCategory: CalendarQueryCategory,
    ) {
        val result = runCatching {
            runBlocking {
                calendarQueryClassifier.classify(
                    message = text, timezone = timezone, receivedAt = now
                )
            }
        }.getOrThrow()

        assertThat(result).isNotNull
        assertThat(result.category).isEqualTo(expectedCategory)

    }
}