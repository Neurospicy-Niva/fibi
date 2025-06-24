package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.domain.model.Task
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId

@Component
class AddTaskSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val taskRepository: TaskRepository,
    val friendshipLedger: FriendshipLedger,
) : CrudSubtaskHandler<NewTaskInformation, Task>(
    intent = TaskIntents.Add, entityHandler = object : CrudEntityHandler<NewTaskInformation, Task> {

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
            return NoActionResolutionResult() // not needed for Add
        }

        override suspend fun extractEntityData(
            rawText: String,
            previousData: NewTaskInformation?,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): ExtractionResult<NewTaskInformation> {
            val previousDataList = previousData?.let {
                "The following fields were found previously: ${
                    listOfNotNull(
                        previousData.title,
                        previousData.description,
                        previousData.completed?.let { "state of completed" }).joinToString { "\"$it\"" }
                }))}"
            } ?: ""
            val prompt = """
                You are helping the user create a new task.

                A task has:
                - title (required): A short descriptive name
                - description (optional): Additional details
                - completed (optional): Whether the task is already done

                This is a multi-turn conversation. Sometimes the user gives only partial information. You will be asked again for missing fields.
                $previousDataList

                ✅ Extract only what the user clearly wants to set.
                ❌ Do NOT guess. Leave fields out if they are not provided.

                Return only a valid JSON object with:
                - title
                - description
                - completed

                Conversation:
                "$rawText"
                ${if (!clarificationQuestion.isNullOrBlank()) "---\n\"$clarificationQuestion\"" else ""}
                ${if (!answer.isNullOrBlank()) "---\n\"$answer\"" else ""}
            """.trimIndent()

            val resultJson = llmClient.promptReceivingJson(
                listOf(UserMessage(prompt)),
                OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime
            ) ?: return ExtractionResult()

            val json = objectMapper.readTree(resultJson)
            val title = json["title"]?.asText()?.takeIf { it.isNotBlank() } ?: previousData?.title
            val description = json["description"]?.asText()?.takeIf { it.isNotBlank() } ?: previousData?.description
            val completed = json["completed"]?.asBoolean() ?: previousData?.completed

            val entity = NewTaskInformation(title, description, completed)

            return ExtractionResult(
                data = entity,
                missingFields = entity.missingFields,
            )
        }
    }, friendshipLedger
) {

    override suspend fun loadEntities(friendshipId: FriendshipId): List<Task> {
        return emptyList() // not needed for Add
    }

    override suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: NewTaskInformation) {
        if (!entity.complete) return
        taskRepository.save(
            Task(
                owner = friendshipId,
                title = entity.title!!,
                description = entity.description,
                completed = entity.completed ?: false,
                completedAt = if (entity.completed == true) Instant.now() else null
            )
        )
    }
}


data class NewTaskInformation(
    val title: String? = null, val description: String? = null, val completed: Boolean? = false,
) {
    val complete: Boolean get() = title != null
    val missingFields: List<String>
        get() = if (complete) emptyList() else listOf("title") + (if (description.isNullOrBlank()) listOf("description") else emptyList()) + (if (completed == null) listOf(
            "completed"
        ) else emptyList())
}