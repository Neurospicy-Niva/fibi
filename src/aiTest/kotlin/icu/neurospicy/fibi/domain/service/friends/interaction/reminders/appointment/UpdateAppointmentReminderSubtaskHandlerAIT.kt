package icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.service.friends.interaction.Goal
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContext
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateAppointmentReminderSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var updateAppointmentReminderSubtaskHandler: UpdateAppointmentReminderSubtaskHandler

    @BeforeEach
    override fun setUp() {
        super.setUp()
        reminderRepository.setReminder(
            AppointmentReminder(
                _id = "update-test-id",
                owner = friendshipId,
                matchingTitleKeywords = setOf("doctor"),
                text = "Pick up prescription",
                offset = Duration.ofMinutes(10),
                remindBeforeAppointment = true
            )
        )
    }

    @Test
    fun `can handle subtasks of UpdateAppointmentReminder intent`() {
        assertTrue {
            updateAppointmentReminderSubtaskHandler.canHandle(
                Subtask(SubtaskId("appt-update"), AppointmentReminderIntents.Update)
            )
        }
    }

    @ParameterizedTest
    @MethodSource("update appointment reminder messages")
    fun `updates appointment reminder correctly`(
        userMessage: String,
        expectedText: String?,
        expectedKeywords: Set<String>?,
        expectedOffset: Duration?,
        expectedBefore: Boolean?
    ) = runBlocking<Unit> {
        val msg = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), userMessage, Channel.SIGNAL
        )
        val subtask = Subtask(
            SubtaskId("appt-update"), AppointmentReminderIntents.Update, msg.text, mapOf("rawText" to msg.text)
        )
        val context =
            GoalContext(Goal(AppointmentReminderIntents.Update), originalMessage = msg, subtasks = listOf(subtask))

        val (_, clarification, updatedSubtask) = updateAppointmentReminderSubtaskHandler.handle(
            subtask,
            context,
            friendshipId
        )

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)

        val reminder = reminderRepository.findAppointmentRemindersBy(friendshipId).first { it._id == "update-test-id" }

        expectedText?.let { assertThat(reminder.text).containsIgnoringCase(it) }
        expectedKeywords?.forEach {
            assertThat(reminder.matchingTitleKeywords.map { it.lowercase() }).contains(it.lowercase())
        }
        expectedOffset?.let { assertEquals(it, reminder.offset) }
        expectedBefore?.let { assertEquals(it, reminder.remindBeforeAppointment) }
    }

    companion object {
        @JvmStatic
        fun `update appointment reminder messages`(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "Update my reminder to say: Take your insurance card", "insurance card", null, null, null
            ), Arguments.of(
                "Change the offset to 5 minutes and remind me after the appointment",
                null,
                null,
                Duration.ofMinutes(5),
                false
            ), Arguments.of(
                "Update reminder keywords to include 'dentist' and 'clinic'",
                null,
                setOf("dentist", "clinic"),
                null,
                null
            ), Arguments.of(
                "Remind me 10 minutes before doctor appointments to pick up my prescription",
                "prescription",
                setOf("doctor"),
                Duration.ofMinutes(10),
                true
            )
        )
    }
}

