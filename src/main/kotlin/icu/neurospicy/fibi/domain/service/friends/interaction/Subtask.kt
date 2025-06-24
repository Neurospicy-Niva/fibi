package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.domain.model.SubtaskStatus
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineStepId
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Represents a subtask of a goal.
 */
data class Subtask(
    val id: SubtaskId,
    val intent: Intent,
    val description: String = "",
    val parameters: Map<String, Any?> = emptyMap(),
    val status: SubtaskStatus = SubtaskStatus.Pending,
) {
    fun needsClarification(): Boolean = status == SubtaskStatus.InClarification
    fun completed(): Boolean = status == SubtaskStatus.Completed || status == SubtaskStatus.Aborted
}

@JvmInline
value class SubtaskId(private val id: String) {
    override fun toString(): String = id

    companion object {
        @JvmStatic
        fun from(
            friendshipId: FriendshipId, intent: Intent, messageId: MessageId,
        ) = SubtaskId(listOf(friendshipId, intent, messageId).joinToString { it.toString() })

        @JvmStatic
        fun from(
            friendshipId: FriendshipId, routineStepId: RoutineStepId,
        ) = SubtaskId(listOf(friendshipId, routineStepId).joinToString { it.toString() })
    }
}

@ConsistentCopyVisibility
data class SubtaskClarificationResponse private constructor(
    val updatedContext: GoalContext,
    val clarificationQuestion: String? = null,
    val successMessageGenerationPrompt: String? = null,
    val hasProcessingError: Boolean = false,
) {
    fun clarified(): Boolean = !hasProcessingError && clarificationQuestion == null

    companion object {
        fun stillNeedsClarification(
            context: GoalContext,
            updatedSubtask: Subtask,
            clarificationQuestion: String,
            successMessageGenerationPrompt: String?,
        ): SubtaskClarificationResponse {
            return SubtaskClarificationResponse(
                context.copy(subtasks = context.subtasks.filterNot { it.id == updatedSubtask.id } + updatedSubtask,
                    subtaskClarificationQuestions = context.subtaskClarificationQuestions.filterNot { it.relatedSubtask == updatedSubtask.id } + SubtaskClarificationQuestion(
                        clarificationQuestion, updatedSubtask.id
                    ),
                    lastUpdated = Instant.now()), clarificationQuestion,
                successMessageGenerationPrompt = successMessageGenerationPrompt)
        }

        fun clarifiedSubtask(
            context: GoalContext,
            updatedSubtask: Subtask,
            successMessageGenerationPrompt: String?,
        ): SubtaskClarificationResponse {
            return SubtaskClarificationResponse(
                context.copy(
                    subtasks = context.subtasks.filterNot { it.id == updatedSubtask.id } + updatedSubtask,
                    subtaskClarificationQuestions = context.subtaskClarificationQuestions.filterNot { it.relatedSubtask == updatedSubtask.id },
                    lastUpdated = Instant.now()
                ),
                successMessageGenerationPrompt = successMessageGenerationPrompt
            )
        }

        fun failed(context: GoalContext, errorMessage: String): SubtaskClarificationResponse {
            LOG.error("Failed clarification with error: $errorMessage")
            return SubtaskClarificationResponse(
                context.copy(
                    lastUpdated = Instant.now()
                ), hasProcessingError = true
            )
        }

        fun aborted(
            context: GoalContext,
            relatedSubtask: Subtask,
            successMessageGenerationPrompt: String?,
        ): SubtaskClarificationResponse {
            LOG.debug("Aborted clarification with relatedSubtask: ${relatedSubtask.description}")
            return SubtaskClarificationResponse(
                updatedContext = context.copy(
                    subtasks = context.subtasks - relatedSubtask.copy(
                        status = SubtaskStatus.Aborted
                    )
                ),
                successMessageGenerationPrompt = successMessageGenerationPrompt
            )
        }

        val LOG = LoggerFactory.getLogger(this::class.java)
    }
}