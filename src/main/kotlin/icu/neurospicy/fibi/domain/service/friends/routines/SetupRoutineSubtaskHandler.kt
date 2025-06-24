package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.*

/**
 * Handles interaction for setting up a new routine. Thus, asks for the routine, for all needed parameters and triggers SetupRoutine to do the magic.
 */
class SetupRoutineSubtaskHandler(
    private val templateRepository: RoutineTemplateRepository,
    private val setupRoutine: SetupRoutine,
    private val extractor: ExtractParamFromConversationService,
) : SubtaskHandler {
    override suspend fun handle(
        subtask: Subtask,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskResult {
        val templateId = context.parameters["routineTemplateId"] as? RoutineTemplateId
            ?: return SubtaskResult.failure("Missing routineTemplateId", subtask)

        val template = templateRepository.findById(templateId) ?: return SubtaskResult.failure(
            "Routine template not found", subtask
        )

        if (template.setupSteps.none { it is ParameterRequestStep }) {
            setupRoutine.execute(template.templateId, friendshipId, emptyMap())
            return SubtaskResult.success(
                "The routine \"${template.title}\" was successfully set up. All necessary parameters were already provided or none required.",
                subtask
            )
        }
        val firstParameterStep = template.setupSteps.firstOrNull { it is ParameterRequestStep } as ParameterRequestStep

        return SubtaskResult.needsClarification(
            subtask, firstParameterStep.description
        )
    }

    override suspend fun tryResolveClarification(
        subtask: Subtask,
        clarificationQuestion: SubtaskClarificationQuestion,
        answer: UserMessage,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskClarificationResult {
        val templateId = context.parameters["routineTemplateId"] as? RoutineTemplateId
            ?: return SubtaskClarificationResult.failure("Missing routineTemplateId", subtask)

        val template = templateRepository.findById(templateId)
            ?: return SubtaskClarificationResult.failure("Routine template not found", subtask)

        val currentParameterStep = template.setupSteps.firstOrNull { step ->
            step is ParameterRequestStep && !subtask.parameters.containsKey(step.parameterKey)
        } as? ParameterRequestStep
            ?: return SubtaskClarificationResult.failure("No matching parameter request step found", subtask)


        val extractionResult = try {
            when (currentParameterStep.parameterType) {
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
        } catch (e: Exception) {
            return SubtaskClarificationResult.failure("Llm threw exception: $e", subtask)
        }
        if (extractionResult.failed) return SubtaskClarificationResult.failure("Failed hard to extract result", subtask)

        val updatedParameters = if (extractionResult.completed) {
            subtask.parameters + mapOf(currentParameterStep.parameterKey to extractionResult.value)
        } else {
            return SubtaskClarificationResult.needsClarification(
                subtask, extractionResult.clarifyingQuestion ?: currentParameterStep.description
            )
        }
        val remainingSteps = template.setupSteps.filterIsInstance<ParameterRequestStep>()
            .filter { !updatedParameters.containsKey(it.parameterKey) }

        return if (remainingSteps.isEmpty()) {
            setupRoutine.execute(
                template.templateId,
                friendshipId,
                updatedParameters.mapNotNull { (key, value) -> if (value != null) key to value else null }.toMap()
            )
            SubtaskClarificationResult.success("The routine was successfully set up.", subtask)
        } else {
            val nextStep = remainingSteps.first()
            SubtaskClarificationResult.needsClarification(
                subtask.copy(parameters = updatedParameters), nextStep.description
            )
        }
    }

    override fun canHandle(intent: Intent): Boolean {
        return intent == RoutineIntents.Setup
    }
}

