package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant.now

class ReminderClassifierAIT : BaseAIT() {


    @Autowired
    lateinit var classifier: TimeBasedReminderClassifier

    @ParameterizedTest
    @MethodSource("set reminders examples")
    fun `classification contains expected parts when extracting set reminder tasks`(
        message: String, expectedTextPartsPerTask: List<Set<String>>
    ) = runBlocking {
        val userMessage = UserMessage(SignalMessageId(now().epochSecond), now(), message, Channel.SIGNAL)
        val extracted = classifier.extractAddReminders(friendshipId, userMessage)
        assertThat(extracted).withFailMessage { "Expected at least one extracted task for message: \"$message\"" }.isNotEmpty
        assertThat(extracted.size).withFailMessage { "Should find ${expectedTextPartsPerTask.size} parts about reminders" }
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


    @ParameterizedTest
    @MethodSource("update reminders examples")
    fun `classification contains expected parts when extracting update reminder tasks`(
        message: String, expectedTextPartsPerTask: List<Set<String>>
    ) = runBlocking {
        val userMessage = UserMessage(SignalMessageId(now().epochSecond), now(), message, Channel.SIGNAL)
        val extracted = classifier.extractUpdateReminders(friendshipId, userMessage)
        assertThat(extracted).withFailMessage { "Expected at least one extracted task for message: \"$message\"" }.isNotEmpty
        assertThat(extracted.size).withFailMessage { "Should find ${expectedTextPartsPerTask.size} parts about reminders" }
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

    @ParameterizedTest
    @MethodSource("list reminders examples")
    fun `classification contains expected parts when extracting list reminder tasks`(
        message: String, expectedTextPartsPerTask: List<Set<String>>
    ) = runBlocking {
        val userMessage = UserMessage(SignalMessageId(now().epochSecond), now(), message, Channel.SIGNAL)
        val extracted = classifier.extractListReminders(friendshipId, userMessage)
        assertThat(extracted).withFailMessage { "Expected at least one extracted task for message: \"$message\"" }.isNotEmpty
        assertThat(extracted.size).withFailMessage { "Should find ${expectedTextPartsPerTask.size} parts about reminders" }
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
        fun `set reminders examples`(): List<Arguments> = listOf(
            Arguments.of(
                "Remind me tomorrow at noon", listOf(setOf("remind me tomorrow at noon"))
            ),
            Arguments.of(
                "Remind me at 10 pm to turn off the air purifier", listOf(setOf("10 pm", "air purifier", "off"))
            ),
            Arguments.of(
                "I forgot my pencil at the office. Set a reminder for 2 pm tomorrow to look for my pencil!",
                listOf(setOf("tomorrow", "2 pm", "pencil"))
            ),
            Arguments.of(
                "Yeaha, in two weeks is Janes birthday. Please remind me on sunday at 3 pm to make the cake for them. It shall be a carrot cake.",
                listOf(setOf("sunday at 3 pm", "make", "cake", "jane", "carrot"))
            ),
        )

        @JvmStatic
        fun `update reminders examples`(): List<Arguments> = listOf(
            Arguments.of(
                "Change the reminder about making cake. Rename from cake to carrot cake.",
                listOf(setOf("reminder about making cake", "carrot cake"))
            ),
            Arguments.of(
                "Change my reminder for 2 pm. Set text to \"Read the paper\"",
                listOf(setOf("Change my reminder for 2 pm. Set text to \"Read the paper\""))
            ),

            )

        @JvmStatic
        fun `list reminders examples`(): List<Arguments> = listOf(
            Arguments.of(
                "Show my reminders", listOf(setOf("Show my reminders"))
            ),
            Arguments.of(
                "Yesterday I had an excellent cake. Well, show all my reminders.",
                listOf(setOf("show all my reminders"))
            ),
        )
    }
}