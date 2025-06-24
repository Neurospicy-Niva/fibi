package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment.AppointmentReminderIntents
import icu.neurospicy.fibi.domain.service.friends.interaction.timers.TimerIntents
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class ReminderGoalDeterminatorAIT : BaseAIT() {
    @Autowired
    private lateinit var reminderGoalDeterminator: ReminderGoalDeterminator

    @ParameterizedTest
    @MethodSource("expected intents for messages")
    fun `reminder determines messages correctly`(message: String, expectedIntents: Set<Intent>) = runBlocking<Unit> {
        // Arrange
        val receivedAt = Instant.now()
        val userMessage =
            UserMessage(SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL, message)
        // Act
        val goals = reminderGoalDeterminator.determineGoal(GeneralReminderIntents.List, userMessage, friendshipId)
        // Assert
        expectedIntents.forEach { expectedIntent ->
            assertThat(goals.map { it.intent }).contains(expectedIntent)
        }
    }

    companion object {
        @JvmStatic
        fun `expected intents for messages`(): List<Arguments> {
            return listOf(
                // reminders
                Arguments.of("Add a reminder for 12 pm", setOf(ReminderIntents.Set)),
                Arguments.of("Remove the reminder for 12 pm", setOf(ReminderIntents.Remove)),
                Arguments.of("Change the reminder for 12 pm", setOf(ReminderIntents.Update)),
                Arguments.of("Show my time-based reminders", setOf(ReminderIntents.List)),
                // appointment reminders
                Arguments.of("Add a reminder for train journeys", setOf(AppointmentReminderIntents.Set)),
                Arguments.of("Remove the reminder for train journeys", setOf(AppointmentReminderIntents.Remove)),
                Arguments.of("Change the reminder for train journeys", setOf(AppointmentReminderIntents.Update)),
                Arguments.of("Show my reminders for appointments", setOf(AppointmentReminderIntents.List)),
                // timers
                Arguments.of("Please set a timer for 1:22 minutes to 'Look for the tea'", setOf(TimerIntents.Set)),
                Arguments.of("Remove the timer", setOf(TimerIntents.Remove)),
                Arguments.of("Set the timer to 15 minutes instead of 20", setOf(TimerIntents.Update)),
                Arguments.of("Which timers are running?", setOf(TimerIntents.List)),
                // general reminders
                Arguments.of("Add a reminder", setOf(ReminderIntents.Set, AppointmentReminderIntents.Set)),
                Arguments.of(
                    "Remove a reminder", setOf(ReminderIntents.Remove, AppointmentReminderIntents.Remove)
                ),
                Arguments.of(
                    "Change a reminder", setOf(ReminderIntents.Update, AppointmentReminderIntents.Update)
                ),
                Arguments.of("Show my reminders", setOf(GeneralReminderIntents.List)),
            )
        }
    }
}