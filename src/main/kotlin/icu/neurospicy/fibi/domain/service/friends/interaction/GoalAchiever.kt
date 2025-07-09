package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Service
import java.time.ZoneOffset

@Service
class GoalAchiever(
    private val subtaskHandlers: List<SubtaskHandler>,
    private val llmClient: LlmClient,
    private val friendshipLedger: FriendshipLedger,
    private val complexTaskModel: String,
) {
    /**
     * Advances the goal by attempting to complete subtasks.
     * Returns the result of the advancement attempt.
     */
    suspend fun advance(context: GoalContext, friendshipId: FriendshipId, message: UserMessage): GoalAdvancementResult {
        // If there are no subtasks, the goal is already complete
        if (context.subtasks.isEmpty()) {
            return GoalAdvancementResult.completed(context)
        }

        // If all subtasks are completed, the goal is complete
        if (context.subtasks.all { it.completed() }) {
            return GoalAdvancementResult.completed(context)
        }

        // Process all uncompleted subtasks in sequence
        val subtaskResults = coroutineScope {
            context.subtasks.filter { !it.completed() }.mapNotNull { subtask ->
                val handlers = subtaskHandlers.filter { it.canHandle(subtask) }
                if (handlers.isEmpty()) {
                    LOG.error("No handler for subtask ${subtask.intent} found!")
                    return@mapNotNull null
                } else if (handlers.size > 1) {
                    LOG.error("Multiple handlers for subtask ${subtask.intent} found, processing with ${handlers.first().javaClass}")
                }
                return@mapNotNull async { handlers.first().handle(subtask, context, friendshipId) }
            }.map { it.await() }
        }

        val responseGenerationPrompts = subtaskResults.mapNotNull { it.successMessageGenerationPrompt }.toList()
        val contextWithAddedParameters = context.copy(
            parameters = if (subtaskResults.all { it.updatedContextParameters.isEmpty() }) context.parameters else context.parameters.plus(
                subtaskResults.map { it.updatedContextParameters }
                    .reduce { acc, list -> acc + list })
        )
        return when {
            subtaskResults.mapNotNull { it.subtaskClarificationQuestion }.toList()
                .isNotEmpty() -> GoalAdvancementResult.subtaskNeedsClarification(
                contextWithAddedParameters,
                subtaskResults.map { it.updatedSubtask }.toList(),
                subtaskResults.mapNotNull { it.subtaskClarificationQuestion }.toList(),
                responseGenerationPrompts
            )

            subtaskResults.all { it.updatedSubtask.completed() } -> GoalAdvancementResult.completed(
                contextWithAddedParameters,
                subtaskResults.map { it.updatedSubtask }.toList(), responseGenerationPrompts,

                )

            else -> GoalAdvancementResult.ongoing(
                contextWithAddedParameters, subtaskResults.map { it.updatedSubtask }.toList(), responseGenerationPrompts
            )
        }
    }

    /**
     * Handles clarification responses from the user.
     * This could be either goal clarification or subtask clarification.
     */
    suspend fun handleClarification(
        friendshipId: FriendshipId, context: GoalContext, message: UserMessage,
    ): SubtaskClarificationResponse = coroutineScope {
        val userWantsToAbortCall = async { verifyIfUserWantsToAbort(friendshipId, message) }
        // Find a subtask that needs clarification
        val subtaskClarificationQuestion = context.subtaskClarificationQuestions.firstOrNull()
            ?: return@coroutineScope SubtaskClarificationResponse.failed(
                context, "No subtask needs clarification"
            )
        val relatedSubtask = context.subtasks.firstOrNull { it.id == subtaskClarificationQuestion.relatedSubtask }
            ?: return@coroutineScope SubtaskClarificationResponse.failed(
                context, "No subtask for subtask clarification found"
            )

        val handler = subtaskHandlers.firstOrNull { it.canHandle(relatedSubtask) }
            ?: return@coroutineScope SubtaskClarificationResponse.failed(
                context, "Internal error while handling subtask clarification."
            )
        if (userWantsToAbortCall.await()) {
            return@coroutineScope SubtaskClarificationResponse.aborted(
                context,
                relatedSubtask,
                successMessageGenerationPrompt = "Tell the user that Subtask \"${relatedSubtask.description}\" was aborted according to their request!"
            )
        }
        return@coroutineScope handler.tryResolveClarification(
            relatedSubtask, subtaskClarificationQuestion, message, context, friendshipId
        ).let {
            if (it.hasProcessingError) {
                SubtaskClarificationResponse.failed(
                    context.copy(
                        parameters = context.parameters.plus(it.updatedContextParameters)
                    ), "Error while handling subtask clarification."
                )
            } else if (it.clarificationNeeded) {
                val questions = it.subtaskClarificationQuestion?.text
                SubtaskClarificationResponse.stillNeedsClarification(
                    context.copy(
                        parameters = context.parameters.plus(it.updatedContextParameters)
                    ), it.updatedSubtask, """
                    Ask the user for clarification to continue with the task ${it.updatedSubtask.description}. The following question must be asked:
                    $questions
                    Make it easy and friendly to answer.
                """.trimIndent(), successMessageGenerationPrompt = it.successMessageGenerationPrompt
                )
            } else {
                SubtaskClarificationResponse.clarifiedSubtask(
                    context.copy(
                        parameters = context.parameters.plus(it.updatedContextParameters)
                    ), it.updatedSubtask, successMessageGenerationPrompt = it.successMessageGenerationPrompt
                )
            }
        }
    }


    suspend fun verifyIfUserWantsToAbort(friendshipId: FriendshipId, message: UserMessage): Boolean {
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC
        return llmClient.promptReceivingText(
            listOf(
                SystemMessage("You are a intent detector."), org.springframework.ai.chat.messages.UserMessage(
                    """
                    Given the message:
                    "${message.text}"
                    ---
                    Does the user explicitly intend to abort the current task?
                    Return yes or no. No explanation, no chat."
                """.trimIndent()
                )
            ),
            OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.8).build(),
            timezone,
            message.receivedAt
        )?.trim()?.lowercase()?.startsWith("yes") ?: false
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}
