// GoalContext.kt
package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.UserMessage
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Represents the current context of a conversation goal.
 */
data class GoalContext(
    val goal: Goal? = null,
    val originalMessage: UserMessage? = null,
    val goalClarificationQuestion: GoalClarificationQuestion? = null,
    val subtasks: List<Subtask> = emptyList(),
    val parameters: Map<String, Any?> = emptyMap(),
    val lastUpdated: Instant = Instant.now(),
    val subtaskClarificationQuestions: List<SubtaskClarificationQuestion> = emptyList(),
) {
    fun pendingGoalClarification(): Boolean = goalClarificationQuestion != null
    fun pendingSubtaskClarification(): Boolean = subtaskClarificationQuestions.isNotEmpty()

    companion object {
        fun none() = GoalContext()
        fun unknown() = GoalContext()
    }
}

/**
 * Represents a clarification question that needs to be answered.
 */
data class GoalClarificationQuestion(
    val prompt: String, val intents: Set<Intent>,
)

/**
 * Result of advancing a goal.
 */
@ConsistentCopyVisibility
data class GoalAdvancementResult private constructor(
    val updatedContext: GoalContext,
    val subtaskSuccessGenerationPrompts: List<String> = emptyList(),
) {
    fun clarificationNeeded() = updatedContext.subtaskClarificationQuestions.isNotEmpty()
    fun complete() = updatedContext.subtasks.all { it.completed() }


    companion object {
        fun subtaskNeedsClarification(
            context: GoalContext,
            updatedSubtasks: List<Subtask>,
            clarificationQuestions: List<SubtaskClarificationQuestion>,
            subtaskSuccessGenerationPrompts: List<String> = emptyList(),
        ): GoalAdvancementResult = GoalAdvancementResult(
            context.copy(
                subtaskClarificationQuestions = clarificationQuestions,
                subtasks = context.subtasks.filterNot { it.id in updatedSubtasks.map { subtask -> subtask.id } } + updatedSubtasks),
            subtaskSuccessGenerationPrompts = subtaskSuccessGenerationPrompts,
        )

        fun completed(
            context: GoalContext,
            updatedSubtasks: List<Subtask> = emptyList(),
            subtaskSuccessGenerationPrompts: List<String> = emptyList(),
        ): GoalAdvancementResult = GoalAdvancementResult(
            context.copy(subtasks = context.subtasks.filterNot { it.id in updatedSubtasks.map { subtask -> subtask.id } } + updatedSubtasks,
                goalClarificationQuestion = null,
                subtaskClarificationQuestions = emptyList()),
            subtaskSuccessGenerationPrompts = subtaskSuccessGenerationPrompts,
        )

        fun ongoing(
            context: GoalContext,
            updatedSubtasks: List<Subtask>,
            subtaskSuccessGenerationPrompts: List<String> = emptyList(),
        ): GoalAdvancementResult = GoalAdvancementResult(
            context.copy(subtasks = context.subtasks.filterNot { it.id in updatedSubtasks.map { subtask -> subtask.id } } + updatedSubtasks),
            subtaskSuccessGenerationPrompts = subtaskSuccessGenerationPrompts,
        )
    }
}

@ConsistentCopyVisibility
data class GoalClarificationResponse private constructor(
    val questionGenerationPrompt: String? = null,
    val intent: Intent? = null,
    val processingError: Boolean = false,
) {
    fun clarified() = !processingError && intent != null

    companion object {
        fun clarified(intent: Intent): GoalClarificationResponse = GoalClarificationResponse(intent = intent)
        fun needsClarification(questionGenerationPrompt: String): GoalClarificationResponse =
            GoalClarificationResponse(questionGenerationPrompt = questionGenerationPrompt)

        fun failed(errorMessage: String): GoalClarificationResponse {
            LOG.error("Failed to clarify goal: $errorMessage")
            return GoalClarificationResponse(processingError = true)
        }

        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}


/**
 * Response from handling an unstructured intent (like smalltalk) within a goal.
 */
data class UnstructuredResponse(
    val updatedContext: GoalContext, val responseGenerationPrompt: String? = null,
)

data class Goal(
    val intent: Intent, val description: String = intent.name,
)
