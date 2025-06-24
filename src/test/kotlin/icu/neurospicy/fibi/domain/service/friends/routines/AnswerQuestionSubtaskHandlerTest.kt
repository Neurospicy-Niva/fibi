package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.interaction.ExtractParamFromConversationService
import icu.neurospicy.fibi.domain.service.friends.interaction.ExtractParamResult
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineParameterSet
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalTime
import java.util.*

class AnswerQuestionSubtaskHandlerTest {

    @Test
    fun `completes subtask when extracting parameter from subtasks succeeds`() = runBlocking<Unit> {
        val stepWithQuestion = ParameterRequestStep(
            "Are you ready to take breakfast?",
            "ready",
            parameterType = RoutineParameterType.BOOLEAN,
            timeOfDay = TimeOfDayLocalTime(LocalTime.of(10, 0))
        )
        val currentPhase = RoutinePhase(
            "Start small", steps = listOf(
                stepWithQuestion,
                ActionRoutineStep("Eat breakfast", true, timeOfDay = TimeOfDayLocalTime(LocalTime.of(10, 10))),
            )
        )
        val routineTemplate = RoutineTemplate(
            title = "Morning routine", version = "1.0", description = "Morning routine", phases = listOf(
                currentPhase,
            )
        )
        val friendshipId = FriendshipId()
        val routineInstance = RoutineInstance(
            templateId = routineTemplate.templateId,
            friendshipId = friendshipId,
            currentPhaseId = currentPhase.id,
            _id = UUID.randomUUID().toString()
        )
        val templateRepository = mockk<RoutineTemplateRepository>() {
            every { findById(routineTemplate.templateId) } returns routineTemplate
        }
        val routineRepository = mockk<RoutineRepository>(relaxed = true) {
            every { findById(friendshipId, routineInstance.instanceId) } returns routineInstance
        }
        val extractor = mockk<ExtractParamFromConversationService>(relaxed = true) {
            coEvery { extractBoolean(any(), any()) } returns ExtractParamResult(value = true)
        }
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val subtask = Subtask(
            SubtaskId.from(friendshipId, stepWithQuestion.id), RoutineIntents.AnswerQuestion, parameters = mapOf(
                "routineInstanceId" to routineInstance.instanceId, "routineStepId" to stepWithQuestion.id
            )
        )
        val answerQuestionSubtaskHandler = AnswerQuestionSubtaskHandler(
            extractor, routineRepository, templateRepository, eventPublisher, mockk(relaxed = true)
        )
        // Act
        val clarificationResult = answerQuestionSubtaskHandler.tryResolveClarification(
            subtask,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            friendshipId,
        )
        // Assert
        assertThat(clarificationResult).extracting { it.updatedSubtask.completed() }.isEqualTo(true)
        verify {
            eventPublisher.publishEvent(
                match { it is RoutineParameterSet && it.friendshipId == friendshipId && it.instanceId == routineInstance.instanceId && it.phaseId == routineInstance.currentPhaseId && it.stepId == stepWithQuestion.id && it.parameterKey == stepWithQuestion.parameterKey })
        }
    }

}