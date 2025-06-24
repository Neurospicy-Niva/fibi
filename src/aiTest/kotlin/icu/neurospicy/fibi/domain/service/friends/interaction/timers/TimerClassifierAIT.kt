package icu.neurospicy.fibi.domain.service.friends.interaction.timers

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant.now

class TimerClassifierAIT : BaseAIT() {

    @Autowired
    lateinit var classifier: TimerClassifier

    @ParameterizedTest
    @MethodSource("classification examples")
    fun `shoould contain expected parts when classifying message`(
        message: String,
        expectedTextPartsPerTask: List<Set<String>>
    ) =
        runBlocking {
            val userMessage = UserMessage(SignalMessageId(now().epochSecond), now(), message, Channel.SIGNAL)
            val extracted = classifier.extractSetTimers(friendshipId, userMessage)
            println(extracted)
            assertThat(extracted).withFailMessage { "Expected at least one extracted task for message: \"$message\"" }.isNotEmpty
            assertThat(extracted.size).withFailMessage { "Should find ${expectedTextPartsPerTask.size} parts about timers" }
                .isEqualTo(expectedTextPartsPerTask.size)
            expectedTextPartsPerTask.forEach { expectedTextParts ->
                expectedTextParts.forEach { expectedText ->
                    assertThat(extracted).anySatisfy {
                        assertThat(it.relevantText).withFailMessage { "Expecting ${it.relevantText} to contain $expectedText" }
                            .containsIgnoringCase(expectedText)
                    }
                }
            }
        }

    companion object {
        @JvmStatic
        fun `classification examples`(): List<Arguments> = listOf(
            Arguments.of(
                "Please set a timer for 1:22 minutes to 'Look for the tea'",
                listOf(setOf("1:22 minutes", "Look for the tea"))
            ),
            Arguments.of(
                "Set a 30-minute timer with text 'Make cake for the birthday'",
                listOf(setOf("30-minute", "Make cake for the birthday"))
            ),
            Arguments.of(
                "Give me a 30-minute timer for 'Pizza is ready'",
                listOf(setOf("30-minute", "Pizza is ready"))
            ),
            Arguments.of("I want a 20 minute timer to water the garden", listOf(setOf("20 minute", "water", "garden"))),
            Arguments.of("Timer: 5m for eggs", listOf(setOf("5m for eggs"))),
            Arguments.of(
                "Timer: 5m for eggs. Timer: 3 min for tea",
                listOf(setOf("5m for eggs"), setOf("3 min for tea"))
            ),
            Arguments.of(
                "Set a 30-minute timer with text 'Make cake for the birthday' and another for 5 minutes",
                listOf(setOf("30-minute", "Make cake for the birthday"), setOf("5 minutes"))
            ),
            Arguments.of(
                "Set a timer for an hour and another for 11 minutes. The first is about waking up my daughter. The second is for the pizza in the oven.",
                listOf(setOf("an hour", "waking", "daughter"), setOf("11 minutes", "pizza"))
            )
        )
    }
}