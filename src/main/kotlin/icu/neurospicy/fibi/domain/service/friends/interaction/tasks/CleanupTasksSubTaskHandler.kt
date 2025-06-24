package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset

@Component
class CleanupTasksSubTaskHandler(
    private val taskRepository: TaskRepository,
    private val friendshipLedger: FriendshipLedger,
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
) : SubtaskHandler {
    override fun canHandle(intent: Intent): Boolean = intent == TaskIntents.Cleanup

    override suspend fun handle(
        subtask: Subtask, context: GoalContext, friendshipId: FriendshipId,
    ): SubtaskResult {
        if (!canHandle(subtask)) return SubtaskResult.failure("Subtask is not supported", subtask)
        val countOfIncompleteTasks = taskRepository.findByFriendshipId(friendshipId).filter { it.completed }.size
        if (countOfIncompleteTasks == 0) return SubtaskResult.success(
            "The user requested to cleanup completed tasks, but there are no completed tasks. Tell them everything is alright.",
            subtask
        )

        return SubtaskResult.needsClarification(
            subtask,
            "Do you really want to clean up $countOfIncompleteTasks completed tasks?"
        )
    }

    override suspend fun tryResolveClarification(
        subtask: Subtask,
        clarificationQuestion: SubtaskClarificationQuestion,
        answer: UserMessage,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskClarificationResult {
        val rawText =
            subtask.parameters["rawText"] as? String ?: return SubtaskClarificationResult.failure(
                "Missing rawText",
                subtask
            )
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC
        val messageTime = context.originalMessage?.receivedAt ?: Instant.now()
        val countOfIncompleteTasks = taskRepository.findByFriendshipId(friendshipId).filter { it.completed }.size

        val resultJson = llmClient.promptReceivingJson(
            listOf(
                org.springframework.ai.chat.messages.UserMessage(
                    """
You are a conversational chat bot who supports task management.

Determine if the user really intends to cleanup their $countOfIncompleteTasks completed tasks.
Return only a JSON object having fields:
- decision: Either agree, disagree or unclear
- clarifyingQuestion: Optional clarifying question if the user's intent is unclear

Conversation:
$rawText
---
${clarificationQuestion.text}
---
${answer.text}
---

No explanation. Just respond the JSON object.
        """.trimIndent()
                )
            ), OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.8).build(), timezone, messageTime
        )

        val jsonNodes = objectMapper.readTree(resultJson)
        val decision = jsonNodes["decision"].asText()
        return when (decision.lowercase()) {
            "agree" -> {
                taskRepository.cleanUp(friendshipId, messageTime)
                SubtaskClarificationResult.success(updatedSubtask = subtask)
            }

            "disagree" -> SubtaskClarificationResult.success(updatedSubtask = subtask)
            else -> {
                val question = jsonNodes["clarifyingQuestion"]?.asText()
                    ?: "Do you really want to clean up $countOfIncompleteTasks completed tasks?"
                SubtaskClarificationResult.needsClarification(
                    subtask, question
                )
            }
        }
    }
}