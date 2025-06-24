package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateReminderSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var updateReminderSubtaskHandler: UpdateReminderSubtaskHandler

    @BeforeEach
    override fun setUp() {
        super.setUp()
        friendshipLedger.updateZoneId(friendshipId, ZonedDateTime.now().zone)
    }

    @Test
    fun `can handle subtasks of UpdateTimeBasedReminder intent`() {
        assertTrue {
            updateReminderSubtaskHandler.canHandle(
                Subtask(
                    SubtaskId("42"),
                    ReminderIntents.Update
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("update reminder expectations")
    fun `extracts expected parts when updating reminder`(
        originalText: String,
        originalRemindAt: ZonedDateTime,
        text: String,
        expectedText: String,
        expectedRemindAt: ZonedDateTime
    ) {
        // Given
        insertTimeBasedReminder(originalText, originalRemindAt)
        val receivedAt = Instant.now()
        val userMessage = UserMessage(SignalMessageId(receivedAt.epochSecond), receivedAt, text, Channel.SIGNAL)
        val subtask = Subtask(
            SubtaskId("reminder-1"),
            ReminderIntents.Update,
            text,
            parameters = mapOf("rawText" to text)
        )
        val context =
            GoalContext(Goal(ReminderIntents.Update), originalMessage = userMessage, subtasks = listOf(subtask))

        // When
        val (_, clarification, updatedSubtask) = runBlocking {
            updateReminderSubtaskHandler.handle(
                subtask,
                context,
                friendshipId
            )
        }

        // Then
        assertNull(clarification, "Should not have a clarification: ${clarification?.text}")
        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        val updated = reminderRepository.findTimeBasedRemindersBy(friendshipId).first()
        assertThat(updated.text).containsIgnoringCase(expectedText)
        assertThat(updated.trigger.localTime.atZone(updated.trigger.timezone))
            .isCloseTo(expectedRemindAt, within(2, ChronoUnit.MINUTES))
    }

    private fun insertTimeBasedReminder(text: String, remindAt: ZonedDateTime): String {
        val trigger = DateTimeBasedTrigger(remindAt.toLocalDateTime(), remindAt.zone)
        val reminder = Reminder(owner = friendshipId, trigger = trigger, text = text)
        return reminderRepository.setReminder(reminder)._id!!
    }

    companion object {
        @JvmStatic
        fun `update reminder expectations`(): List<Arguments> {
            val now = ZonedDateTime.now(ZoneId.of("UTC"))
            return listOf(
                Arguments.of(
                    "Do the dishes",
                    now.plusWeeks(1).withHour(9).withMinute(0).withSecond(0).withNano(0),
                    "Update the reminder for doing the dishes to doing homework",
                    "homework",
                    now.plusWeeks(1).withHour(9).withMinute(0).withSecond(0).withNano(0),
                ),
                Arguments.of(
                    "Call Alex",
                    now.plusWeeks(1).withHour(9).withMinute(0).withSecond(0).withNano(0),
                    "Update the reminder to call Alex: remind me tomorrow at 3pm",
                    "call Alex",
                    now.plusDays(1).withHour(15).withMinute(0).withSecond(0).withNano(0),
                ),
                Arguments.of(
                    "Take out trash",
                    now.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0),
                    "Change my reminder about trash. Set it to next saturday at 9 pm",
                    "trash",
                    now.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SATURDAY))
                        .withHour(21).withMinute(0).withSecond(0).withNano(0)
                ),
                Arguments.of(
                    "Call Alex",
                    now.plusWeeks(1).withHour(9).withMinute(0).withSecond(0).withNano(0),
                    "change my reminder to call alex. actually, i want to call jane.",
                    "call jane",
                    now.plusWeeks(1).withHour(9).withMinute(0).withSecond(0).withNano(0),
                )
            )
        }
    }
}