package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aParameterRequestStep
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineTemplate
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aMessageStep
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineInstance
import icu.neurospicy.fibi.outgoing.ollama.ExtractionException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

class SetupRoutineSubtaskHandlerTest {

    private val templateRepository = mockk<RoutineTemplateRepository>(relaxed = true)
    private val setupRoutine = mockk<SetupRoutine>(relaxed = true)
    private val extractor = mockk<ExtractParamFromConversationService>(relaxed = true)
    private val handler = SetupRoutineSubtaskHandler(templateRepository, setupRoutine, extractor)

    @Test
    fun `should ask for next parameter if first was resolved`() = runBlocking<Unit> {
        val parameterStep1 = aParameterRequestStep()
        val parameterStep2 = aParameterRequestStep {
            question = "What activity do you want to include?"
            parameterKey = "morningActivity"
            parameterType = RoutineParameterType.STRING
        }
        val template = aRoutineTemplate { setupSteps = listOf(parameterStep1, parameterStep2) }
        coEvery { templateRepository.findById(template.templateId) } returns template
        coEvery { extractor.extractLocalString(any(), any()) } returns ExtractParamResult(value = "some time")

        val friendshipId = FriendshipId()
        val subtask = setupSubtask(friendshipId)
        val clarification = SubtaskClarificationQuestion(parameterStep1.question, subtask.id)
        val context = goalContextWith(template.templateId)

        val result = handler.tryResolveClarification(subtask, clarification, someMessage(), context, friendshipId)

        assertThat(result.updatedSubtask.completed()).isFalse()
        assertThat(result.subtaskClarificationQuestion).isNotNull
            .extracting { it!!.text }
            .isEqualTo(parameterStep2.question)
    }

    @Test
    fun `should fail if routine template cannot be loaded`() = runBlocking {
        coEvery { templateRepository.findById(any()) } returns null

        val friendshipId = FriendshipId()
        val subtask = setupSubtask(friendshipId)
        val context = GoalContext(
            originalMessage = null,
            parameters = mapOf("routineTemplateId" to RoutineTemplateId("invalid:template"))
        )

        val result = handler.handle(subtask, context, friendshipId)

        assertFalse(result.updatedSubtask.completed())
        assertThat(result.successMessageGenerationPrompt).isNull()
    }

    @Test
    fun `should immediately setup routine if no parameters are needed`() = runBlocking<Unit> {
        val template = aRoutineTemplate { setupSteps = emptyList() }
        coEvery { templateRepository.findById(template.templateId) } returns template
        every { setupRoutine.execute(any(), any(), any()) } returns aRoutineInstance { this.template = template }

        val friendshipId = FriendshipId()
        val subtask = setupSubtask(friendshipId)
        val context = goalContextWith(template.templateId)
        val result = handler.handle(subtask, context, friendshipId)

        verify { setupRoutine.execute(template.templateId, friendshipId, emptyMap()) }
        assertThat(result.successMessageGenerationPrompt).contains("successfully set up")
        assertThat(result.updatedSubtask.completed()).isTrue()
    }

    @Test
    fun `should ask for first parameter if setupSteps include ParameterRequestStep`() = runBlocking<Unit> {
        val parameterStep = aParameterRequestStep()
        val template = aRoutineTemplate { setupSteps = listOf(parameterStep) }
        coEvery { templateRepository.findById(template.templateId) } returns template

        val friendshipId = FriendshipId()
        val subtask = setupSubtask(friendshipId)
        val context = goalContextWith(template.templateId)

        val result = handler.handle(subtask, context, friendshipId)

        assertThat(result.subtaskClarificationQuestion)
            .isNotNull
            .extracting { it!!.text }
            .isEqualTo(parameterStep.description)
        assertThat(result.updatedSubtask.completed()).isFalse()
    }

    @Test
    fun `should fail clarification resolution if template is not found`() = runBlocking<Unit> {
        coEvery { templateRepository.findById(any()) } returns null
        val friendshipId = FriendshipId()
        val templateId = RoutineTemplateId("nonexistent:template")

        val subtask = Subtask(
            SubtaskId.from(friendshipId, RoutineIntents.Setup, SignalMessageId(Instant.now().epochSecond)),
            RoutineIntents.Setup,
            parameters = mapOf("routineTemplateId" to templateId)
        )
        val clarification = SubtaskClarificationQuestion("When do you want to start your routine?", subtask.id)
        val context = goalContextWith(templateId)

        val result = handler.tryResolveClarification(subtask, clarification, someMessage(), context, friendshipId)

        assertThat(result.successMessageGenerationPrompt).isNull()
        assertThat(result.updatedSubtask.completed()).isFalse()
    }

    @Test
    fun `should handle clarification resolution`() = runBlocking<Unit> {
        val parameterStep = aParameterRequestStep()
        val template = aRoutineTemplate { setupSteps = listOf(parameterStep) }
        coEvery { templateRepository.findById(any()) } returns template
        every { setupRoutine.execute(any(), any(), any()) } returns mockk(relaxed = true)
        coEvery { extractor.extractLocalString(any(), any()) } returns ExtractParamResult(value = "some other time")

        val friendshipId = FriendshipId()
        val subtask = setupSubtask(friendshipId)
        val clarification = SubtaskClarificationQuestion("When do you want to start your routine?", subtask.id)
        val context = goalContextWith(template.templateId)

        val result = handler.tryResolveClarification(subtask, clarification, someMessage(), context, friendshipId)

        assertThat(result.successMessageGenerationPrompt).isNotNull.containsIgnoringCase("successfully set up")
        assertThat(result.updatedSubtask.completed()).isTrue()
    }

    @Test
    fun `should fail when extraction fails`() = runBlocking<Unit> {
        val parameterStep = aParameterRequestStep()
        val template = aRoutineTemplate { setupSteps = listOf(parameterStep) }
        coEvery { templateRepository.findById(any()) } returns template
        coEvery { extractor.extractLocalString(any(), any()) } throws ExtractionException("Meh", Exception("Meh"))

        val friendshipId = FriendshipId()
        val subtask = setupSubtask(friendshipId)
        val clarification = SubtaskClarificationQuestion("When do you want to start your routine?", subtask.id)
        val context = goalContextWith(template.templateId)

        val result = handler.tryResolveClarification(subtask, clarification, someMessage(), context, friendshipId)

        assertThat(result.successMessageGenerationPrompt).isNull()
        assertThat(result.updatedSubtask.completed()).isFalse()
        assertThat(result.hasProcessingError).isTrue()
    }

    private fun setupSubtask(friendshipId: FriendshipId): Subtask = Subtask(
        SubtaskId.from(friendshipId, RoutineIntents.Setup, SignalMessageId(Instant.now().epochSecond)),
        RoutineIntents.Setup
    )

    private fun someMessage() = UserMessage(SignalMessageId(Instant.now().epochSecond), Instant.now(), "7am", SIGNAL)

    private fun goalContextWith(templateId: RoutineTemplateId) = GoalContext(
        originalMessage = null,
        parameters = mapOf("routineTemplateId" to templateId)
    )

    private fun aRoutineTemplate(block: RoutineTemplateBuilder.() -> Unit = {}): RoutineTemplate =
        RoutineTemplateBuilder().apply(block).build()

    private fun aParameterRequestStep(block: ParameterRequestStepBuilder.() -> Unit = {}): ParameterRequestStep =
        ParameterRequestStepBuilder().apply(block).build()
}

class RoutineTemplateBuilder {
    var title: String = "Test Routine"
    var version: String = "1.0"
    var description: String = "A routine for testing"
    var setupSteps: List<RoutineStep> = emptyList()
    var phases: List<RoutinePhase> = listOf(
        RoutinePhase(
            title = "Test Phase",
            steps = listOf(aMessageStep())
        )
    )

    fun build(): RoutineTemplate = RoutineTemplate(
        _id = UUID.randomUUID().toString(),
        title = title,
        version = version,
        description = description,
        setupSteps = setupSteps,
        phases = phases
    )
}

class ParameterRequestStepBuilder {
    var question: String = "What's your favorite color?"
    var parameterKey: String = "favoriteColor"
    var parameterType: RoutineParameterType = RoutineParameterType.STRING

    fun build(): ParameterRequestStep = ParameterRequestStep(
        question = question,
        parameterKey = parameterKey,
        parameterType = parameterType
    )
}