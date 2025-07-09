package icu.neurospicy.fibi.domain.service.friends.interaction

import com.ninjasquad.springmockk.MockkBean
import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.timers.TimerIntents
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class GoalAchieverAIT : BaseAIT() {
    @Autowired
    private lateinit var goalAchiever: GoalAchiever

    @MockkBean(
        name = "testSubtaskHandler",   // any name that isn’t already in the list
        relaxed = true
    )
    lateinit var subtaskHandler: SubtaskHandler


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

    @Test
    fun `should add parameters to context on subtask handling`() = runBlocking<Unit> {
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), "Test", Channel.SIGNAL
        )
        val testIntent = Intent("TestIntent")
        val subtask = Subtask(
            SubtaskId.from(friendshipId, testIntent, userMessage.messageId),
            testIntent,
            "Test subtask",
            mapOf("rawText" to "Test")
        )
        val goalContext = GoalContext(
            Goal(testIntent, "Test"),
            userMessage,
            subtasks = listOf(subtask),
        )
        every { subtaskHandler.canHandle(testIntent) } returns true
        every { subtaskHandler.canHandle(subtask) } returns true
        coEvery {
            subtaskHandler.handle(
                any(), any(), friendshipId
            )
        } returns SubtaskResult.success(
            updatedSubtask = subtask, updatedContextParameters = mapOf("testParameter" to "testValue")
        )
        // Act
        val response = goalAchiever.advance(goalContext, friendshipId, userMessage)
        // Assert
        assertThat(response.updatedContext.parameters["testParameter"]).isEqualTo("testValue")
        assertThat(response.updatedContext.subtasks).allSatisfy { it.completed() }
    }

    @Test
    fun `should add parameters to context on clarification handling`() = runBlocking<Unit> {
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), "Test", Channel.SIGNAL
        )
        val testIntent = Intent("TestIntent")
        val subtask = Subtask(
            SubtaskId.from(friendshipId, testIntent, userMessage.messageId),
            testIntent,
            "Test subtask",
            mapOf("rawText" to "Test")
        )
        val goalContext = GoalContext(
            Goal(testIntent, "Test"), userMessage, subtasks = listOf(subtask), subtaskClarificationQuestions = listOf(
                SubtaskClarificationQuestion("Which text shall be send on timer expiry?", subtask.id)
            )
        )
        every { subtaskHandler.canHandle(testIntent) } returns true
        every { subtaskHandler.canHandle(subtask) } returns true
        coEvery {
            subtaskHandler.tryResolveClarification(
                subtask, any(), any(), goalContext, friendshipId
            )
        } returns SubtaskClarificationResult.success(
            updatedSubtask = subtask, updatedContextParameters = mapOf("testParameter" to "testValue")
        )
        // Act
        val response = goalAchiever.handleClarification(friendshipId, goalContext, userMessage)
        // Assert
        assertThat(response.updatedContext.parameters["testParameter"]).isEqualTo("testValue")
        assertThat(response.updatedContext.subtasks).allSatisfy { it.completed() }
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