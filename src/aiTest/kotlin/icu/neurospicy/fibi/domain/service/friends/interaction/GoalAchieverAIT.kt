package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.timers.TimerIntents
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class GoalAchieverAIT : BaseAIT() {
    @Autowired
    private lateinit var goalAchiever: GoalAchiever


    @ParameterizedTest
    @MethodSource("subtask abortion cases")
    fun `should classify intents with high confidence`(abortionMessage: String) = runBlocking<Unit> {
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), abortionMessage, Channel.SIGNAL
        )
        val subtask = Subtask(
            SubtaskId("42"),
            TimerIntents.Set,
            "Set timer for 12 minutes",
            mapOf("rawText" to "Set timer for 12 minutes")
        )
        val goalContext = GoalContext(
            Goal(TimerIntents.Set, "Set timer for 12 minutes"),
            userMessage,
            subtasks = listOf(subtask),
            subtaskClarificationQuestions = listOf(
                SubtaskClarificationQuestion("Which text shall be send on timer expiry?", subtask.id)
            )
        )
        // Act
        val clarificationResponse = goalAchiever.handleClarification(friendshipId, goalContext, userMessage)
        // Assert
        assertThat(clarificationResponse.clarified()).isTrue()
        assertThat(clarificationResponse.successMessageGenerationPrompt).containsIgnoringCase("aborted")
    }

    companion object {
        @JvmStatic
        fun `subtask abortion cases`() = listOf(
            Arguments.of("Actually, never mind. I don't want to set a timer anymore."),
            Arguments.of("Skip adding setting timer"),
            Arguments.of("Skip it."),
            Arguments.of("I don't want the timer anymore")
        )
    }
}