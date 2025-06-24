package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SubtaskStatus
import icu.neurospicy.fibi.domain.model.UserMessage
import org.slf4j.LoggerFactory

interface SubtaskHandler {
    suspend fun handle(subtask: Subtask, context: GoalContext, friendshipId: FriendshipId): SubtaskResult
    suspend fun tryResolveClarification(
        subtask: Subtask,
        clarificationQuestion: SubtaskClarificationQuestion,
        answer: UserMessage,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskClarificationResult

    fun canHandle(subtask: Subtask): Boolean = canHandle(subtask.intent)
    fun canHandle(intent: Intent): Boolean
}

data class SubtaskClarificationQuestion(
    val text: String, val relatedSubtask: SubtaskId,
)

@ConsistentCopyVisibility
data class SubtaskResult private constructor(
    val successMessageGenerationPrompt: String? = null,
    val subtaskClarificationQuestion: SubtaskClarificationQuestion? = null,
    val updatedSubtask: Subtask,
    val updatedContextParameters: Map<String, Any?> = emptyMap(),
) {
    companion object {
        fun failure(errorMessage: String, updatedSubtask: Subtask): SubtaskResult {
            LOG.error("Subtask failed: $errorMessage")
            return SubtaskResult(updatedSubtask = updatedSubtask.copy(status = SubtaskStatus.Failed))
        }

        fun needsClarification(
            updatedSubtask: Subtask,
            clarificationQuestion: String,
            successMessagePrompt: String? = null,
        ): SubtaskResult {
            val subtaskNeedingClarification = SubtaskClarificationQuestion(
                clarificationQuestion, updatedSubtask.id,
            )
            return SubtaskResult(
                subtaskClarificationQuestion = subtaskNeedingClarification, updatedSubtask = updatedSubtask.copy(
                    status = SubtaskStatus.InClarification
                ), successMessageGenerationPrompt = successMessagePrompt
            )
        }

        fun success(
            responseGenerationPrompt: String? = null,
            updatedSubtask: Subtask,
            updatedContextParameters: Map<String, Any?> = emptyMap(),
        ): SubtaskResult {
            return SubtaskResult(
                successMessageGenerationPrompt = responseGenerationPrompt,
                updatedSubtask = updatedSubtask.copy(status = SubtaskStatus.Completed),
                updatedContextParameters = updatedContextParameters
            )
        }

        fun inProgress(updatedSubtask: Subtask, successMessagePrompt: String? = null): SubtaskResult {
            return SubtaskResult(
                updatedSubtask = updatedSubtask.copy(status = SubtaskStatus.InProgress),
                successMessageGenerationPrompt = successMessagePrompt
            )
        }

        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}

@ConsistentCopyVisibility
data class SubtaskClarificationResult private constructor(
    val subtaskClarificationQuestion: SubtaskClarificationQuestion? = null,
    val successMessageGenerationPrompt: String? = null,
    val hasProcessingError: Boolean = false,
    val updatedSubtask: Subtask,
    val updatedContextParameters: Map<String, Any?> = emptyMap(),
) {
    val clarificationNeeded: Boolean get() = subtaskClarificationQuestion != null

    companion object {
        fun needsClarification(
            updatedSubtask: Subtask, subtaskClarificationQuestion: String,
        ) = SubtaskClarificationResult(
            SubtaskClarificationQuestion(subtaskClarificationQuestion, updatedSubtask.id),
            updatedSubtask = updatedSubtask.copy(status = SubtaskStatus.InClarification)
        )

        fun failure(error: String, updatedSubtask: Subtask): SubtaskClarificationResult {
            LOG.error("Subtask clarification failure: $error")
            return SubtaskClarificationResult(
                hasProcessingError = true, updatedSubtask = updatedSubtask.copy(status = SubtaskStatus.Failed)
            )
        }

        fun success(
            successMessageGenerationPrompt: String? = null,
            updatedSubtask: Subtask,
            updatedContextParameters: Map<String, Any?> = emptyMap(),
        ): SubtaskClarificationResult {
            return SubtaskClarificationResult(
                successMessageGenerationPrompt = successMessageGenerationPrompt,
                updatedSubtask = updatedSubtask.copy(status = SubtaskStatus.Completed),
                updatedContextParameters = updatedContextParameters
            )
        }

        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}