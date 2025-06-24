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
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListAppointmentRemindersSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var listAppointmentRemindersSubtaskHandler: ListAppointmentRemindersSubtaskHandler


    @BeforeEach
    override fun setUp() {
        super.setUp()
        reminderRepository.setReminder(
            AppointmentReminder(
                owner = friendshipId,
                matchingTitleKeywords = setOf("doctor"),
                text = "Pick up prescription",
                offset = Duration.ofMinutes(10),
                remindBeforeAppointment = true
            )
        )
        reminderRepository.setReminder(
            AppointmentReminder(
                owner = friendshipId,
                matchingTitleKeywords = setOf("meeting"),
                text = "Take water bottle",
                offset = Duration.ofMinutes(5),
                remindBeforeAppointment = true
            )
        )
    }

    @Test
    fun `can handle subtasks of ListAppointmentReminder intent`() {
        assertTrue {
            listAppointmentRemindersSubtaskHandler.canHandle(
                Subtask(SubtaskId("appt-1"), AppointmentReminderIntents.List)
            )
        }
    }

    @Test
    fun `returns appointment reminder list if matching`() = runBlocking<Unit> {
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond),
            Instant.now(),
            "What appointment reminders do I have?",
            Channel.SIGNAL
        )
        val subtask = Subtask(
            SubtaskId("appt-2"), AppointmentReminderIntents.List, userMessage.text, mapOf("rawText" to userMessage.text)
        )
        val context = GoalContext(
            Goal(AppointmentReminderIntents.List),
            originalMessage = userMessage,
            subtasks = listOf(subtask)
        )
        val (prompt, clarification, updatedSubtask) = listAppointmentRemindersSubtaskHandler.handle(
            subtask, context, friendshipId
        )
        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)
        assertNotNull(prompt)
        assertThat(prompt).contains("Pick up prescription")
        assertThat(prompt).contains("Take water bottle")
        assertThat(prompt).contains("doctor")
        assertThat(prompt).contains("meeting")
    }

    @Test
    fun `returns filtered appointment reminders for keyword search`() = runBlocking<Unit> {
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), "Show reminders for meetings", Channel.SIGNAL
        )
        val subtask = Subtask(
            SubtaskId("appt-3"), AppointmentReminderIntents.List, userMessage.text, mapOf("rawText" to userMessage.text)
        )
        val context = GoalContext(
            Goal(AppointmentReminderIntents.List),
            originalMessage = userMessage,
            subtasks = listOf(subtask)
        )
        val (prompt, clarification, updatedSubtask) = listAppointmentRemindersSubtaskHandler.handle(
            subtask, context, friendshipId
        )
        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)
        assertNotNull(prompt)
        assertThat(prompt).contains("Take water bottle")
        assertThat(prompt).doesNotContain("Pick up prescription")
    }

    @Test
    fun `responds when no appointment reminders are present`() = runBlocking<Unit> {
        // Remove all reminders
        reminderRepository.findAppointmentRemindersBy(friendshipId).forEach {
            reminderRepository.removeAppointmentReminder(friendshipId, it._id!!)
        }
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), "List all appointment reminders", Channel.SIGNAL
        )
        val subtask = Subtask(
            SubtaskId("appt-4"), AppointmentReminderIntents.List, userMessage.text, mapOf("rawText" to userMessage.text)
        )
        val context = GoalContext(
            Goal(AppointmentReminderIntents.List),
            originalMessage = userMessage,
            subtasks = listOf(subtask)
        )
        val (prompt, clarification, updatedSubtask) = listAppointmentRemindersSubtaskHandler.handle(
            subtask, context, friendshipId
        )
        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)
        assertThat(prompt).containsIgnoringCase("no appointment reminders")
    }
}