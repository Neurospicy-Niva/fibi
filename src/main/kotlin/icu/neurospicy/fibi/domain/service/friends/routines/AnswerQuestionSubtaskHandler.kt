package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.routines.events.SetRoutineParameterRoutineStep
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AnswerQuestionSubtaskHandler(
    private val extractor: ExtractParamFromConversationService,
    private val routineRepository: RoutineRepository,
    private val templateRepository: RoutineTemplateRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val eventLog: RoutineEventLog,
) : SubtaskHandler {
    override suspend fun handle(
        subtask: Subtask,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskResult {
        return SubtaskResult.needsClarification(
            subtask,
            subtask.parameters["question"] as? String ?: subtask.parameters["question"]?.toString()
            ?: return SubtaskResult.failure("No question available", subtask)
        )
    }

    override suspend fun tryResolveClarification(
        subtask: Subtask,
        clarificationQuestion: SubtaskClarificationQuestion,
        answer: UserMessage,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskClarificationResult {
        val routineInstanceId = subtask.parameters["routineInstanceId"] as? RoutineInstanceId
            ?: return SubtaskClarificationResult.failure("No routine instance id available", subtask)
        val stepId = subtask.parameters["routineStepId"] as? RoutineStepId
            ?: return SubtaskClarificationResult.failure("No step id available", subtask)
        val instance = routineRepository.findById(friendshipId, routineInstanceId)
            ?: return SubtaskClarificationResult.failure("Failed to load routine instance $routineInstanceId", subtask)
        val template = templateRepository.findById(instance.templateId) ?: return SubtaskClarificationResult.failure(
            "Failed to load routine template ${instance.templateId}", subtask
        )
        val step =
            template.phases.firstOrNull { phase -> phase.id == instance.currentPhaseId }?.steps?.firstOrNull { step -> step.id == stepId } as? ParameterRequestStep
                ?: return SubtaskClarificationResult.failure(
                    "Failed to get step $stepId from template ${instance.templateId} in phase ${instance.currentPhaseId}",
                    subtask
                )
        val result = when (step.parameterType) {
            RoutineParameterType.LOCAL_TIME -> extractor.extractLocalTime(clarificationQuestion.text, answer.text)
            RoutineParameterType.DATE -> extractor.extractInstant(clarificationQuestion.text, answer.text)
            RoutineParameterType.STRING -> extractor.extractLocalString(clarificationQuestion.text, answer.text)
            RoutineParameterType.INT -> extractor.extractNumber(
                clarificationQuestion.text, answer.text, Int::class.java
            )

            RoutineParameterType.FLOAT -> extractor.extractNumber(
                clarificationQuestion.text, answer.text, Float::class.java
            )

            RoutineParameterType.BOOLEAN -> extractor.extractBoolean(clarificationQuestion.text, answer.text)
        }
        if (!result.completed) {
            return SubtaskClarificationResult.needsClarification(
                subtask,
                result.clarifyingQuestion ?: clarificationQuestion.text
            )
        }

        routineRepository.save(
            instance.copy(
                parameters = instance.parameters.plus(step.parameterKey to result.value!!),
                progress = instance.progress.copy(
                    iterations = instance.progress.iterations - instance.progress.iterations.first() + instance.progress.iterations.first()
                        .copy(completedSteps = instance.progress.iterations.first().completedSteps.plus(Completion(step.id)))
                )
            )
        )
        eventPublisher.publishEvent(
            SetRoutineParameterRoutineStep(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                instance.currentPhaseId!!,
                step.id,
                step.parameterKey
            )
        )
        eventLog.log(
            RoutineEventLogEntry(
                instance.instanceId, friendshipId, RoutineEventType.STEP_PARAMETER_SET, Instant.now(),
                mapOf("phaseId" to instance.currentPhaseId, "stepId" to step.id, "parameterKey" to step.parameterKey)
            )
        )
        return SubtaskClarificationResult.success(updatedSubtask = subtask)
    }

    override fun canHandle(intent: Intent): Boolean = intent == RoutineIntents.AnswerQuestion

}