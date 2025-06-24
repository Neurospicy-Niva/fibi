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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoveAppointmentReminderSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var removeAppointmentReminderSubtaskHandler: RemoveAppointmentReminderSubtaskHandler

    private lateinit var exampleReminder: AppointmentReminder

    @BeforeEach
    override fun setUp() {
        super.setUp()
        exampleReminder = reminderRepository.setReminder(
            AppointmentReminder(
                owner = friendshipId,
                matchingTitleKeywords = setOf("doctor"),
                text = "Pick up prescription",
                offset = Duration.ofMinutes(10),
                remindBeforeAppointment = false
            )
        )
    }

    @Test
    fun `can handle subtasks of RemoveAppointmentReminder intent`() {
        assertTrue {
            removeAppointmentReminderSubtaskHandler.canHandle(
                Subtask(
                    SubtaskId("appt-remove"), AppointmentReminderIntents.Remove
                )
            )
        }
    }

    @Test
    fun `removes appointment reminder correctly`() = runBlocking<Unit> {
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond),
            Instant.now(),
            "Remove the reminder about picking up prescriptions after doctor visits",
            Channel.SIGNAL
        )
        val subtask = Subtask(
            SubtaskId("appt-remove"),
            AppointmentReminderIntents.Remove,
            userMessage.text,
            parameters = mapOf("rawText" to userMessage.text)
        )
        val context = GoalContext(
            Goal(AppointmentReminderIntents.Remove),
            originalMessage = userMessage,
            subtasks = listOf(subtask)
        )

        val (_, clarification, updatedSubtask) = removeAppointmentReminderSubtaskHandler.handle(
            subtask,
            context,
            friendshipId
        )

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)
        assertThat(reminderRepository.findAppointmentRemindersBy(friendshipId)).noneMatch {
            it._id == exampleReminder._id
        }
    }
}