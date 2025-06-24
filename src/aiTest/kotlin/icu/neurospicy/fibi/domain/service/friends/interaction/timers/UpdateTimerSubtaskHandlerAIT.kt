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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateTimerSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var updateTimerSubtaskHandler: UpdateTimerSubtaskHandler

    @Autowired
    lateinit var timerRepository: TimerRepository

    @Test
    fun `can handle subtasks of UpdateTimer intent`() {
        assertTrue { updateTimerSubtaskHandler.canHandle(Subtask(SubtaskId("42"), TimerIntents.Update)) }
    }

    @ParameterizedTest
    @MethodSource("update timer examples")
    fun `updates timer from natural language`(
        originalDuration: Duration,
        originalLabel: String,
        updateMessage: String,
        expectedDuration: Duration?,
        expectedLabel: String?
    ) = runBlocking<Unit> {
        val timer = timerRepository.save(
            Timer(
                owner = friendshipId,
                duration = originalDuration,
                startedAt = Instant.now(),
                label = originalLabel
            )
        )
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), updateMessage, Channel.SIGNAL
        )
        val subtask = Subtask(SubtaskId("42"), TimerIntents.Update, updateMessage, mapOf("rawText" to updateMessage))

        val (_, clarification, updatedSubtask) = updateTimerSubtaskHandler.handle(
            subtask,
            GoalContext(Goal(TimerIntents.Update), userMessage, subtasks = listOf(subtask)),
            friendshipId
        )

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)

        val updated = timerRepository.findByFriendshipId(friendshipId).first { it._id == timer._id }
        expectedLabel?.let { assertThat(updated.label).contains(it) }
        expectedDuration?.let { assertThat(updated.duration).isEqualTo(it) }
    }

    companion object {
        @JvmStatic
        fun `update timer examples`() = listOf(
            Arguments.of(
                Duration.ofMinutes(3),
                "tea",
                "Change the tea timer to 5 minutes",
                Duration.ofMinutes(5),
                "tea"
            ),
            Arguments.of(Duration.ofMinutes(10), "laundry", "Rename the laundry timer to cleaning", null, "cleaning"),
            Arguments.of(
                Duration.ofMinutes(1),
                "alarm",
                "Update the alarm timer to 2m and label as break",
                Duration.ofMinutes(2),
                "break"
            )
        )
    }
}