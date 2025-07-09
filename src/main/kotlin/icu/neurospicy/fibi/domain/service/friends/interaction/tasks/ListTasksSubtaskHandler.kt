package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TaskRepository

import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset

@Component
class ListTasksSubtaskHandler(
    private val taskRepository: TaskRepository,
    private val friendshipLedger: FriendshipLedger,
    private val llmClient: LlmClient,
    private val complexTaskModel: String,
) : SubtaskHandler {
    override fun canHandle(intent: Intent): Boolean = intent == TaskIntents.List

    override suspend fun handle(
        subtask: Subtask, context: GoalContext, friendshipId: FriendshipId,
    ): SubtaskResult {
        val tasks = taskRepository.findByFriendshipId(friendshipId)
        val rawText = (subtask.parameters["rawText"] as? String)
        val selectedTasks = if (rawText == null) tasks else {
            val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC
            val messageTime = context.originalMessage?.receivedAt ?: Instant.now()

            if (tasks.isEmpty()) {
                return SubtaskResult.success("Tell the user they have no tasks, yet", subtask)
            }
            val taskListText = tasks.sortedBy { it.title }.groupBy { it.completed }.map { groupedTasks ->
                groupedTasks.key to groupedTasks.value.joinToString("\n") {
                    "- ${it.title}, description: ${it.description ?: "(none)"}, id=${it.id}"
                }
            }.toMap()
            val completedTaskListText = taskListText[true]
            val ongoingTaskListText = taskListText[false]
            val prompt = """
                You are helping to identify which task the user wants to view.

                You are given:
                - a list of tasks
                - a user message
                - optional clarification follow-ups

                🎯 Select ids of tasks to show the user based on user intent.
                
                ‼️Always use tools to easily answer typical requests

                Return comma separated list of ids like:
                "...", "..."

                Ongoing tasks:
                $ongoingTaskListText
                Complete tasks:
                $completedTaskListText

                Message:
                "$rawText"
            """.trimIndent()

            val resultCsv = llmClient.promptReceivingJson(
                listOf(UserMessage(prompt)),
                OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime,
                tools = setOf(ShortCutTaskIdsTools(tasks))
            ) ?: return SubtaskResult.failure("Failed to identify tasks", subtask)

            val taskIds = resultCsv.split(",").map { it.trimIndent().removePrefix("\"").removeSuffix("\"") }
            tasks.filter { it.id in taskIds }
        }
        val responseGenerationPrompt = if (selectedTasks.isNotEmpty()) {
            selectedTasks.sortedBy { it.title }.groupBy { it.completed }.map {
                it.key to it.value.map { task ->
                    """
- ${task.title}
    ${if (task.description != null) "description: ${task.description}" else "<no description>"}
    ${if (task.completed) "is complete" else "is not completed"}
                    """.trimIndent()
                }
            }.toMap().let { groupedTasks ->
                """
The user requested a list of tasks. Tell them, this is the list:
${(groupedTasks[false]?.joinToString("\n") ?: "").ifBlank { "" }}
${(groupedTasks[true]?.joinToString("\n") ?: "").ifBlank { "" }}

NEVER invent tasks! NEVER translate tasks! Keep the original wording. Arrange the tasks nicely.
            """.trimIndent()
            }
        } else {
            "Tell the user, that none of the existing tasks match their request."
        }

        return SubtaskResult.success(responseGenerationPrompt, subtask)
    }

    override suspend fun tryResolveClarification(
        subtask: Subtask,
        clarificationQuestion: SubtaskClarificationQuestion,
        answer: icu.neurospicy.fibi.domain.model.UserMessage,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskClarificationResult {
        return SubtaskClarificationResult.success(updatedSubtask = subtask)
    }

}
