package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.SubtaskStatus
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.Goal
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContext
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetReminderSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var setReminderSubtaskHandler: SetReminderSubtaskHandler

    @BeforeEach
    override fun setUp() {
        super.setUp()
    }

    @Test
    fun `can handle subtasks of SetTimeBasedReminder intent`() {
        assertTrue {
            setReminderSubtaskHandler.canHandle(
                Subtask(
                    SubtaskId("test"),
                    ReminderIntents.Set
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("expected time-based reminders for messages")
    fun `sets a time-based reminder from message`(
        messageText: String,
        expectedTextParts: Set<String>,
        expectedTriggerTime: ZonedDateTime
    ) = runBlocking {
        val receivedAt = Instant.now()
        val userMessage = UserMessage(SignalMessageId(receivedAt.epochSecond), receivedAt, messageText, Channel.SIGNAL)
        val subtask = Subtask(
            SubtaskId("reminder-1"),
            ReminderIntents.Set,
            messageText,
            parameters = mapOf("rawText" to messageText)
        )
        val context = GoalContext(Goal(ReminderIntents.Set), userMessage, subtasks = listOf(subtask))

        val (_, clarification, updatedSubtask) = setReminderSubtaskHandler.handle(subtask, context, friendshipId)

        assertNull(clarification)
        assertThat(reminderRepository.findTimeBasedRemindersBy(friendshipId)).hasSize(1)
        val reminder = reminderRepository.findTimeBasedRemindersBy(friendshipId).first()
        val actualTrigger = reminder.trigger.localTime.atZone(reminder.trigger.timezone)

        assertThat(actualTrigger).isCloseTo(expectedTriggerTime, within(2, ChronoUnit.MINUTES))
        expectedTextParts.forEach {
            assertThat(reminder.text).containsIgnoringCase(it)
        }
        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
    }

    companion object {
        @JvmStatic
        fun `expected time-based reminders for messages`(): List<Arguments> {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            return listOf(
                Arguments.of(
                    "Remind me to clean the house next Monday at noon",
                    setOf("clean", "house"),
                    now.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(12)
                        .withMinute(0).withSecond(0).withNano(0),
                    within(2, ChronoUnit.MINUTES)
                ),
                Arguments.of(
                    "I want to have a spooky night searching for ghosts. Remind me at midnight!",
                    setOf("spooky"),
                    now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
                ),
                Arguments.of(
                    "Remind me at 6pm to join the meeting",
                    setOf("meeting"),
                    now.withHour(18).withMinute(0).withSecond(0).withNano(0).plusDays(if (now.hour >= 18) 1 else 0)
                ),
                Arguments.of(
                    "Please remind me tomorrow at 9:00 AM to call Sarah",
                    setOf("Sarah"),
                    now.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0)
                ),
                Arguments.of(
                    "Remind me on new years eve at 22:30 to greet Peter",
                    setOf("greet peter"),
                    ZonedDateTime.of(now.year, 12, 31, 22, 30, 0, 0, ZoneOffset.UTC)
                ),
                Arguments.of(
                    "Remind me on 24 August at 13:30 to call Peter and wish him a happy birthday.",
                    setOf("peter", "birthday"),
                    ZonedDateTime.of(now.year, 8, 24, 13, 30, 0, 0, ZoneOffset.UTC)
                ),
                Arguments.of(
                    "Set a reminder for next Saturday to look for my plants. 2 PM should be fine.",
                    setOf("plant"),
                    now.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SATURDAY)).withHour(14)
                        .withMinute(0).withSecond(0).withNano(0)
                ),
                Arguments.of(
                    "Last weeks were quite stressy. Let's focus again. Remind me tomorrow at 7 am to go jogging.",
                    setOf("jog"),
                    now.plusDays(1).withHour(7).withMinute(0).withSecond(0).withNano(0)
                )
            )
        }
    }
}