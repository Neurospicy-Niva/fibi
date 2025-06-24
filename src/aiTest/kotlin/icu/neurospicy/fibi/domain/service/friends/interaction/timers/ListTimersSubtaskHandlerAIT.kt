package icu.neurospicy.fibi.domain.service.friends.interaction.timers

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.repository.TimerRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.Goal
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContext
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListTimersSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var listTimersSubtaskHandler: ListTimersSubtaskHandler

    @Autowired
    lateinit var timerRepository: TimerRepository

    @Test
    fun `can handle subtasks of ListTimer intent`() {
        assertTrue { listTimersSubtaskHandler.canHandle(Subtask(SubtaskId("42"), TimerIntents.List)) }
    }

    @Test
    fun `returns timer list if matching`() = runBlocking<Unit> {
        val timer = timerRepository.save(
            Timer(
                owner = friendshipId,
                label = "cooking pasta",
                duration = Duration.ofMinutes(12),
                startedAt = Instant.now()
            )
        )
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), "What timers are running?", Channel.SIGNAL
        )
        val subtask =
            Subtask(SubtaskId("42"), TimerIntents.List, userMessage.text, mapOf("rawText" to userMessage.text))
        val context = GoalContext(Goal(TimerIntents.List), userMessage, subtasks = listOf(subtask))

        val (prompt, clarification, updatedSubtask) = listTimersSubtaskHandler.handle(subtask, context, friendshipId)

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)
        assertNotNull(prompt)
        assertThat(prompt).contains("cooking pasta")
        assertThat(prompt).contains("duration")
    }

    @Test
    fun `responds when no timers are present`() = runBlocking<Unit> {
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), "List all timers", Channel.SIGNAL
        )
        val subtask =
            Subtask(SubtaskId("42"), TimerIntents.List, userMessage.text, mapOf("rawText" to userMessage.text))
        val context = GoalContext(Goal(TimerIntents.List), userMessage, subtasks = listOf(subtask))

        val (prompt, clarification, updatedSubtask) = listTimersSubtaskHandler.handle(subtask, context, friendshipId)

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)
        assertThat(prompt).containsIgnoringCase("no active timers")
    }
}