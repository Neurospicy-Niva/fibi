package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.IncomingFriendMessageReceived
import icu.neurospicy.fibi.domain.service.friends.interaction.calendar.CalendarIntents
import icu.neurospicy.fibi.domain.service.friends.interaction.reminders.ReminderIntents
import icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment.AppointmentReminderIntents
import icu.neurospicy.fibi.domain.service.friends.interaction.tasks.TaskIntents
import icu.neurospicy.fibi.domain.service.friends.interaction.timers.TimerIntents
import icu.neurospicy.fibi.domain.service.friends.interaction.timezones.TimezoneIntents
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant.now
import kotlin.test.assertTrue

class IntentClassifierAIT : BaseAIT() {

    @Autowired
    lateinit var intentClassifier: IntentClassifier

    @ParameterizedTest
    @MethodSource("single message test cases")
    fun `should classify intents with high confidence`(testCase: IntentTestCase) = runBlocking {
        val intents = intentClassifier.classifyIntent(incomingFriendMessageReceived(testCase.message))
        println("${testCase.message}, expected ${testCase.expectedIntents.joinToString()}")
        println(intents.joinToString("\n") { "${it.intent}: ${it.confidence}" })
        assertTrue(
            intents.filterNot { it.confidence < 0.8 }
                .any { it.intent in testCase.expectedIntents },
            "Expected at least one of ${testCase.expectedIntents} with confidence > 0.85, got: $intents"
        )
        assertTrue(
            testCase.expectedIntents.any { expectedIntent -> expectedIntent == intents.maxByOrNull { it.confidence }?.intent },
            "Any of the expected intents must be main intent=${intents.maxByOrNull { it.confidence }?.intent}"
        )
    }

    @ParameterizedTest
    @MethodSource("conversation test cases")
    fun `should classify conversations with expected intent`(testCase: ConversationTestCase) = runBlocking {
        val intents = intentClassifier.classifyIntent(testCase.conversation)
        println(
            "${
                testCase.conversation.messages.joinToString { it.text }.take(40)
            }, expected ${testCase.expectedIntents.joinToString()}"
        )
        println(intents.joinToString("\n") { "${it.intent}: ${it.confidence}" })
        assertTrue(
            intents.filterNot { it.confidence < 0.8 }
                .any { it.intent in testCase.expectedIntents },
            "Expected at least one of ${testCase.expectedIntents} with confidence > 0.85, got: $intents"
        )
        assertTrue(
            testCase.expectedIntents.any { expectedIntent -> expectedIntent == intents.maxByOrNull { it.confidence }?.intent },
            "Any of the expected intents must be main intent"
        )
    }

    companion object {
        @JvmStatic
        fun `conversation test cases`() = listOf(
            ConversationTestCase(
                Conversation(
                    CalendarIntents.Register, listOf(
                        UserMessage(
                            SignalMessageId(now().epochSecond), now(),
                            "I want to register my nextcloud calendar.", Channel.SIGNAL
                        ),
                        FibiMessage(
                            FibiMessageId(),
                            now(),
                            "Please send the calendar URL, often called CalDAV URl.",
                            Channel.SIGNAL,
                            null
                        ),
                        UserMessage(
                            SignalMessageId(now().epochSecond), now(),
                            "Actually, never mind. I no longer want to add a calendar.", Channel.SIGNAL
                        )
                    ), now()
                ), setOf(CoreIntents.CancelGoal)
            )
        )

        @JvmStatic
        fun `single message test cases`() = listOf(
            IntentTestCase("Please add 'do laundry' to my tasks.", setOf(TaskIntents.Add)),
            // Appointment Reminders
            IntentTestCase(
                "When will I be reminded before the next appointment?", setOf(AppointmentReminderIntents.List)
            ),
            IntentTestCase(
                "Delete the reminder about dentist appointment.", setOf(AppointmentReminderIntents.Remove)
            ),
            IntentTestCase(
                "Remind me after therapy appointments to take a walk.", setOf(AppointmentReminderIntents.Set)
            ),
            IntentTestCase(
                "Update the appointment reminder about my meeting with Paul.", setOf(AppointmentReminderIntents.Update)
            ),

            // Reminders
            IntentTestCase("Show me all time-based reminders.", setOf(ReminderIntents.List)),
            IntentTestCase("Remove the reminder to water the garden.", setOf(ReminderIntents.Remove)),
            IntentTestCase(
                "Set a reminder for tomorrow at 10am; Topic is that I need call mom",
                setOf(ReminderIntents.Set)
            ),
            IntentTestCase(
                "Update the reminder about watering the plants at 7 pm.", setOf(ReminderIntents.Update)
            ),

            // Timers
            IntentTestCase("List all active timers.", setOf(TimerIntents.List)),
            IntentTestCase("Remove the egg timer.", setOf(TimerIntents.Remove)),
            IntentTestCase("Set a timer for 20 minutes for pasta.", setOf(TimerIntents.Set)),
            IntentTestCase("Update the timer to 15 minutes.", setOf(TimerIntents.Update)),

            // Tasks
            IntentTestCase("Clean up my finished tasks.", setOf(TaskIntents.Cleanup)),
            IntentTestCase("Mark the reading task as complete.", setOf(TaskIntents.Complete)),
            IntentTestCase("Please add 'do laundry' to my tasks.", setOf(TaskIntents.Add)),
            IntentTestCase("Show me my tasks, please.", setOf(TaskIntents.List)),

            // Timezone
            IntentTestCase("Set my timezone to Europe/Berlin", setOf(TimezoneIntents.Set)),
            IntentTestCase("Set my clock. It is currently 3 pm", setOf(TimezoneIntents.SetClock)),
            IntentTestCase("My current time is 14:12 o'clock", setOf(TimezoneIntents.SetClock)),

            // Calendar
            IntentTestCase("I want to add a new calendar", setOf(CalendarIntents.Register)),
            IntentTestCase("Register calendar", setOf(CalendarIntents.Register)),
            IntentTestCase("Remove my \"Dog\" calendar", setOf(CalendarIntents.Remove)),
            IntentTestCase("Which calendars do I have connected?", setOf(CalendarIntents.ListAppointments)),

            // Additional intent test cases
            IntentTestCase("Tell me a joke.", setOf(CoreIntents.Smalltalk)),
            IntentTestCase("How are you doing today?", setOf(CoreIntents.Smalltalk)),
            IntentTestCase("Forget it.", setOf(CoreIntents.CancelGoal)),
            IntentTestCase("And what about the second one?", setOf(CoreIntents.FollowUp)),
            IntentTestCase("Huh?", setOf(CoreIntents.Smalltalk, CoreIntents.Unknown))
        )
    }

    data class IntentTestCase(
        val message: String, val expectedIntents: Set<Intent>,
    )

    data class ConversationTestCase(
        val conversation: Conversation, val expectedIntents: Set<Intent>,
    )

    private fun incomingFriendMessageReceived(message: String): IncomingFriendMessageReceived =
        IncomingFriendMessageReceived(
            friendshipId, UserMessage(
                SignalMessageId(now().epochSecond), text = message, channel = Channel.SIGNAL
            )
        )
}