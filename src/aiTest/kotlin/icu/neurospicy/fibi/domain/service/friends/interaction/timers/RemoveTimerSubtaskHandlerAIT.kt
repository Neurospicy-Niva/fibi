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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoveTimerSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var removeTimerSubtaskHandler: RemoveTimerSubtaskHandler

    @Autowired
    lateinit var timerRepository: TimerRepository

    @Test
    fun `can handle subtasks of RemoveTimer intent`() {
        assertTrue { removeTimerSubtaskHandler.canHandle(Subtask(SubtaskId("42"), TimerIntents.Remove)) }
    }

    @Test
    fun `removes a specific timer when clearly referenced`() = runBlocking<Unit> {
        val timer = timerRepository.save(
            Timer(owner = friendshipId, duration = Duration.ofMinutes(3), startedAt = Instant.now(), label = "make tea")
        )

        val message = "Cancel the tea timer"
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), message, Channel.SIGNAL
        )
        val subtask = Subtask(SubtaskId("42"), TimerIntents.Remove, message, mapOf("rawText" to message))

        val (_, clarification, updatedSubtask) = removeTimerSubtaskHandler.handle(
            subtask, GoalContext(Goal(TimerIntents.Remove), userMessage, subtasks = listOf(subtask)), friendshipId
        )

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)
        val timers = timerRepository.findByFriendshipId(friendshipId)
        assertThat(timers.none { it._id == timer._id }).isTrue
    }
}