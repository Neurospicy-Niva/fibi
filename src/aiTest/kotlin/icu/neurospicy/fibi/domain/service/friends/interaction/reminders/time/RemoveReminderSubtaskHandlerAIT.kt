package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoveReminderSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var removeReminderSubtaskHandler: RemoveReminderSubtaskHandler

    @BeforeEach
    override fun setUp() {
        super.setUp()
        friendshipLedger.updateZoneId(friendshipId, ZoneId.of("UTC"))
    }

    @Test
    fun `can handle subtasks of RemoveTimeBasedReminder intent`() {
        assertTrue {
            removeReminderSubtaskHandler.canHandle(
                Subtask(SubtaskId("remove-1"), ReminderIntents.Remove)
            )
        }
    }

    @Test
    fun `removes most recent reminder when referring to -the- reminder`() = runBlocking<Unit> {
        insertReminder("Drink tea", ZonedDateTime.now().plusMinutes(10))
        val recent = insertReminder("Call mom", ZonedDateTime.now().plusMinutes(20))

        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond),
            Instant.now(),
            "Delete the reminder",
            Channel.SIGNAL
        )
        val subtask = Subtask(
            SubtaskId("42"),
            ReminderIntents.Remove,
            userMessage.text,
            parameters = mapOf("rawText" to userMessage.text)
        )
        val context = GoalContext(Goal(ReminderIntents.Remove), userMessage, subtasks = listOf(subtask))

        val (_, clarification, updatedSubtask) = removeReminderSubtaskHandler.handle(subtask, context, friendshipId)

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)
        val reminders = reminderRepository.findTimeBasedRemindersBy(friendshipId)
        assertThat(reminders).noneMatch { it.text.contains(recent.text, ignoreCase = true) }
            .noneMatch { it._id == recent._id }
    }

    @ParameterizedTest
    @MethodSource("clarify removal examples")
    fun `resolves clarification for reminder removal`(initialMessage: String, clarificationAnswer: String) =
        runBlocking {
            insertReminder("Drink tea", ZonedDateTime.now().plusMinutes(10))
            val toRemove = insertReminder("Feed cat", ZonedDateTime.now().plusMinutes(30))

            val initialUserMessage = createUserMessage(initialMessage, Instant.now().minusSeconds(10))
            val clarificationUserMessage = createUserMessage(clarificationAnswer)

            val subtask = Subtask(
                SubtaskId("42"),
                ReminderIntents.Remove,
                initialUserMessage.text,
                mapOf("rawText" to initialUserMessage.text)
            )
            val clarificationQuestion = SubtaskClarificationQuestion("Which reminder should I remove?", subtask.id)

            val (subtaskClarificationQuestion, _) = removeReminderSubtaskHandler.tryResolveClarification(
                subtask,
                clarificationQuestion,
                clarificationUserMessage,
                GoalContext(
                    Goal(ReminderIntents.Remove), originalMessage = initialUserMessage,
                    subtasks = listOf(subtask),
                    subtaskClarificationQuestions = listOf(clarificationQuestion)
                ),
                friendshipId
            )

            assertThat(reminderRepository.findTimeBasedRemindersBy(friendshipId))
                .noneMatch { it.text.contains(toRemove.text, ignoreCase = true) }
                .noneMatch { it._id == toRemove._id }
            assertNull(subtaskClarificationQuestion)
        }

    private fun insertReminder(text: String, remindAt: ZonedDateTime): Reminder {
        val reminder = Reminder(
            owner = friendshipId,
            trigger = DateTimeBasedTrigger(remindAt.toLocalDateTime(), remindAt.zone),
            text = text
        )
        reminderRepository.setReminder(reminder)
        return reminder
    }

    private fun createUserMessage(text: String, receivedAt: Instant = Instant.now()) = UserMessage(
        SignalMessageId(receivedAt.epochSecond), receivedAt, text, Channel.SIGNAL
    )

    companion object {
        @JvmStatic
        fun `clarify removal examples`() = listOf(
            Arguments.of("Remove a reminder", "The one about the cat"),
            Arguments.of("Delete a reminder", "Reminder to feed the cat"),
        )
    }
}