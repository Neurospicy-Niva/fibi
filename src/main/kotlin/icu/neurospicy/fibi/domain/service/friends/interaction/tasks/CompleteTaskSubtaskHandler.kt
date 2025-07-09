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
class CompleteTaskSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val taskRepository: TaskRepository,
    val friendshipLedger: FriendshipLedger,
    private val complexTaskModel: String,
) : CrudSubtaskHandler<Unit, Task>(
    intent = TaskIntents.Complete, entityHandler = object : CrudEntityHandler<Unit, Task> {
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
            val taskListText = allEntities.joinToString("\n") {
                "- ${it.title}, description: ${it.description ?: "(none)"}, id=${it.id}"
            }
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
            previousData: Unit?,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): ExtractionResult<Unit> {
            return ExtractionResult(data = Unit)
        }
    }, friendshipLedger
) {
    override suspend fun loadEntities(friendshipId: FriendshipId): List<Task> {
        return taskRepository.findByFriendshipId(friendshipId)
    }

    override suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: Unit) {
        if (id != null) {
            taskRepository.complete(friendshipId, id, true, Instant.now())
        }
    }
}
