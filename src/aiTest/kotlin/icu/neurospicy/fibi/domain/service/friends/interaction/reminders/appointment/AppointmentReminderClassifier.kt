package icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.stream.Stream

class AppointmentReminderClassifierAIT : BaseAIT() {

    @Autowired
    lateinit var classifier: AppointmentReminderClassifier

    private fun msg(text: String): UserMessage {
        val now = Instant.now()
        return UserMessage(SignalMessageId(now.epochSecond), now, text, Channel.SIGNAL)
    }

    @Test
    fun `can extract set appointment reminder`() = runBlocking<Unit> {
        val result =
            classifier.extractSetReminders(friendshipId, msg("Remind me 10 minutes before my dentist appointment"))
        assertThat(result).hasSize(1)
        assertThat(result.first().relevantText).containsIgnoringCase("10 minutes").containsIgnoringCase("dentist")
    }

    @Test
    fun `can extract remove appointment reminder`() = runBlocking<Unit> {
        val result = classifier.extractRemoveReminders(friendshipId, msg("Remove my reminder for meetings"))
        assertThat(result).hasSize(1)
        assertThat(result.first().relevantText).containsIgnoringCase("meetings")
    }

    @Test
    fun `can extract list appointment reminders`() = runBlocking<Unit> {
        val result = classifier.extractListReminders(friendshipId, msg("Which appointment reminders do I have?"))
        assertThat(result).hasSize(1)
        assertThat(result.first().description).containsIgnoringCase("reminder")
    }

    @ParameterizedTest
    @MethodSource("updateReminderExamples")
    fun `can extract update appointment reminder`(text: String, expected: List<String>) = runBlocking {
        val result = classifier.extractUpdateReminders(friendshipId, msg(text))
        assertThat(result).hasSize(1)
        expected.forEach {
            assertThat(result.first().relevantText).containsIgnoringCase(it)
        }
    }

    companion object {
        @JvmStatic
        fun updateReminderExamples(): Stream<Arguments> = Stream.of(
            Arguments.of("Change the dentist reminder to 20 minutes earlier", listOf("dentist", "20 minutes")),
            Arguments.of(
                "Update the reminder about team meeting to notify 5 minutes before",
                listOf("team meeting", "5 minutes")
            ),
            Arguments.of(
                "Change the doctor appointment reminder to remind me 10 minutes after",
                listOf("doctor", "10 minutes")
            )
        )
    }
}