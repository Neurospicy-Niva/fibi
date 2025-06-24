package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.domain.model.Reminder
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.ReminderRepository
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.interaction.prompt.buildEntityIdentificationPrompt
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId

@Component
class RemoveReminderSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val reminderRepository: ReminderRepository,
    friendshipLedger: FriendshipLedger,
) : CrudSubtaskHandler<Unit, Reminder>(
    intent = ReminderIntents.Remove,
    entityHandler = object : CrudEntityHandler<Unit, Reminder> {

        override suspend fun identifyEntityId(
            allEntities: List<Reminder>,
            rawText: String,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): IdResolutionResult {
            val reminderListText = allEntities.joinToString("\n") {
                "- ${it.text}, remindAt: ${it.trigger.localTime} (${it.trigger.timezone}), id=${it._id}"
            }

            val recentReminder = allEntities.maxByOrNull { it.createdAt }?.let {
                "- ${it.text}, remindAt: ${it.trigger.localTime} (${it.trigger.timezone}), id=${it._id}"
            }

            val prompt = buildEntityIdentificationPrompt(
                action = "delete",
                entityName = "reminder",
                entityListText = reminderListText,
                rawText = rawText,
                lastCreatedEntityDescription = recentReminder,
                clarificationQuestion = clarificationQuestion,
                answer = answer
            )

            val resultJson = llmClient.promptReceivingJson(
                listOf(UserMessage(prompt)),
                OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.8).build(),
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
        ): ExtractionResult<Unit> = ExtractionResult(data = Unit)
    },
    friendshipLedger
) {
    override suspend fun loadEntities(friendshipId: FriendshipId): List<Reminder> {
        return reminderRepository.findTimeBasedRemindersBy(friendshipId)
    }

    override suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: Unit) {
        if (id != null) reminderRepository.removeTimeBasedReminder(friendshipId, id)
    }
}