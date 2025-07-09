package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.domain.model.Task
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TaskRepository

import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.interaction.prompt.buildEntityIdentificationPrompt
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Component
class UpdateTaskSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val taskRepository: TaskRepository,
    friendshipLedger: FriendshipLedger,
    private val complexTaskModel: String,
) : CrudSubtaskHandler<TaskChanges, Task>(
    intent = TaskIntents.Update, entityHandler = object : CrudEntityHandler<TaskChanges, Task> {

        private val MINUTES_TASK_IS_EXPECTED_RECENT = 5

        override suspend fun identifyEntityId(
            allEntities: List<Task>,
            rawText: String,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): IdResolutionResult {
            if (allEntities.size == 1) {
                return ClarifiedIdResolutionResult(id = allEntities.first().id)
            }

            val taskListText = allEntities.joinToString("\n") {
                "- ${it.title}, description: ${it.description ?: "(none)"}, id=${it.id}"
            }.ifEmpty { "There are no tasks" }
            val recentlyModifiedTask = allEntities.maxByOrNull { it.lastModifiedAt }?.takeIf {
                ChronoUnit.MINUTES.between(it.lastModifiedAt, Instant.now()) <= MINUTES_TASK_IS_EXPECTED_RECENT
            }?.let { "- ${it.title}, description: ${it.description ?: "(none)"}, id=${it.id}" }
            val prompt = buildEntityIdentificationPrompt(
                action = "delete",
                entityName = "task",
                entityListText = taskListText,
                rawText = rawText,
                lastCreatedEntityDescription = recentlyModifiedTask,
                clarificationQuestion = clarificationQuestion,
                answer = answer
            )

            val resultJson = llmClient.promptReceivingJson(
                listOf(UserMessage(prompt)),
                OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime
            ) ?: return ClarifiedIdResolutionResult()

            val json = objectMapper.readTree(resultJson)
            return ClarifiedIdResolutionResult(
                id = json["id"]?.asText(), clarifyingQuestion = json["clarifyingQuestion"]?.asText()
            )
        }

        override suspend fun extractEntityData(
            rawText: String,
            previousData: TaskChanges?,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): ExtractionResult<TaskChanges> {
            val prompt = """
                You are helping to update a task for the user.

                A task consists of a title, a description and is either completed or not.

                This is an interactive, multi-step conversation. The user provides input in stages. If something is missing, clarification will be requested separately and added later.

                Your task:
                - Extract only the new values the user explicitly wants to apply to an existing task:
                  - title: the new title (optional)
                  - description: the new description (optional)
                  - completed: whether the task should be marked completed (optional)

                ⚠️ Do NOT use information that describes the current task – only what the user intends to CHANGE.
                ⚠️ Do NOT guess or reuse values from the original message unless they are clearly intended as updates.

                ✅ Output a valid JSON object with only the fields to update.
                ❌ Do NOT include explanation or unrelated text.

                Conversation:
                "$rawText"
                ${if (!clarificationQuestion.isNullOrBlank()) "---\n\"$clarificationQuestion\"" else ""}
                ${if (!answer.isNullOrBlank()) "---\n\"$answer\"" else ""}
            """.trimIndent()

            val resultJson = llmClient.promptReceivingJson(
                listOf(UserMessage(prompt)),
                OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime
            ) ?: return ExtractionResult()

            val json = objectMapper.readTree(resultJson)
            val title = json["title"]?.asText()?.takeIf { it.isNotBlank() } ?: previousData?.title
            val description = json["description"]?.asText()?.takeIf { it.isNotBlank() } ?: previousData?.description
            val completed = json["completed"]?.asBoolean() ?: previousData?.completed

            val entity = if (title == null && description == null && completed == null) null
            else TaskChanges(title, description, completed)

            return ExtractionResult(
                data = entity,
            )
        }
    }, friendshipLedger
) {

    override suspend fun loadEntities(friendshipId: FriendshipId): List<Task> {
        return taskRepository.findByFriendshipId(friendshipId)
    }

    override suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: TaskChanges) {
        if (id != null) {
            taskRepository.rename(
                friendshipId, id = id, title = entity.title, description = entity.description
            )
            if (entity.completed != null) {
                taskRepository.complete(friendshipId, id, entity.completed, Instant.now())
            }
        }
    }
}

data class TaskChanges(
    val title: String? = null, val description: String? = null, val completed: Boolean? = null,
)
