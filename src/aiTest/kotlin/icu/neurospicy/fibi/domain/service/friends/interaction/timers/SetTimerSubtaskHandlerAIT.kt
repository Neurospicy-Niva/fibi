package icu.neurospicy.fibi.domain.service.friends.interaction.timers

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.SubtaskStatus
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.TimerRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.Goal
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContext
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetTimerSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var setTimerSubtaskHandler: SetTimerSubtaskHandler

    @Autowired
    lateinit var timerRepository: TimerRepository

    @Test
    fun `can handle subtasks of SetTimer intent`() {
        assertTrue { setTimerSubtaskHandler.canHandle(Subtask(SubtaskId("42"), TimerIntents.Set)) }
    }

    @ParameterizedTest
    @MethodSource("set timer examples")
    fun `sets timer correctly from natural language`(
        message: String,
        expectedDuration: Duration,
        expectedLabel: String?
    ) = runBlocking<Unit> {
        val receivedAt = Instant.now()
        val userMessage = UserMessage(SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL)
        val subtask = Subtask(SubtaskId("42"), TimerIntents.Set, message, mapOf("rawText" to message))

        val (_, clarificationQuestion, updatedSubtask) = setTimerSubtaskHandler.handle(
            subtask, GoalContext(Goal(TimerIntents.Set), userMessage, subtasks = listOf(subtask)), friendshipId
        )

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarificationQuestion)
        val timers = timerRepository.findByFriendshipId(friendshipId)
        assertThat(timers).anySatisfy {
            assertEquals(expectedDuration, it.duration)
            expectedLabel?.apply { assertEquals(expectedLabel.lowercase(), it.label.lowercase()) }
        }
    }

    companion object {
        @JvmStatic
        fun `set timer examples`() = listOf(
            Arguments.of("Set a timer for 30 seconds", Duration.ofSeconds(30), null),
            Arguments.of("I want a 20 minute timer to water the garden", Duration.ofMinutes(20), "water the garden"),
            Arguments.of("Timer: 5m for eggs", Duration.ofMinutes(5), "eggs"),
            Arguments.of(
                "Set a 30-minute timer with text 'Make cake for the birthday'",
                Duration.ofMinutes(30),
                "make cake for the birthday"
            ),
            Arguments.of(
                "Please set a timer for 1:22 minute to 'Look for the tea'",
                Duration.ofMinutes(1).plusSeconds(22),
                "look for the tea"
            ),
        )
    }
}