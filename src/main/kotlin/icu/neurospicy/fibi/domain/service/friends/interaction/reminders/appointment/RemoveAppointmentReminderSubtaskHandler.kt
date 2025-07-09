package icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.AppointmentReminder
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.ReminderRepository

import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.interaction.prompt.buildEntityIdentificationPrompt
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId

@Component
class RemoveAppointmentReminderSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val reminderRepository: ReminderRepository,
    friendshipLedger: FriendshipLedger,
    private val complexTaskModel: String,
) : CrudSubtaskHandler<Unit, AppointmentReminder>(
    intent = AppointmentReminderIntents.Remove,
    entityHandler = object : CrudEntityHandler<Unit, AppointmentReminder> {

        override suspend fun identifyEntityId(
            allEntities: List<AppointmentReminder>,
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
                "- ${it.text}, matchingTitleKeywords: ${it.matchingTitleKeywords}, reminding ${if (it.remindBeforeAppointment) "before" else "after"} appointment, id=${it._id}"
            }

            val recentReminder = allEntities.maxByOrNull { it.createdAt }?.let {
                "- ${it.text}, matchingTitleKeywords: ${it.matchingTitleKeywords}, reminding ${if (it.remindBeforeAppointment) "before" else "after"} appointment, id=${it._id}"
            }

            val prompt = buildEntityIdentificationPrompt(
                action = "delete",
                entityName = "appointment reminder",
                entityListText = reminderListText,
                rawText = rawText,
                lastCreatedEntityDescription = recentReminder,
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
        ): ExtractionResult<Unit> = ExtractionResult(data = Unit)
    },
    friendshipLedger
) {
    override suspend fun loadEntities(friendshipId: FriendshipId): List<AppointmentReminder> {
        return reminderRepository.findAppointmentRemindersBy(friendshipId)
    }

    override suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: Unit) {
        if (id != null) reminderRepository.removeAppointmentReminder(friendshipId, id)
    }
}