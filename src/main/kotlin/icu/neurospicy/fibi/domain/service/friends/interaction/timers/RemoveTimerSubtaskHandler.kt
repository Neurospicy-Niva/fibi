package icu.neurospicy.fibi.domain.service.friends.interaction.timers

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.domain.model.Timer
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TimerRepository

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
class RemoveTimerSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val timerRepository: TimerRepository,
    friendshipLedger: FriendshipLedger,
    private val complexTaskModel: String,
) : CrudSubtaskHandler<Unit, Timer>(
    intent = TimerIntents.Remove,
    entityHandler = object : CrudEntityHandler<Unit, Timer> {

        private val MINUTES_TIMER_IS_EXPECTED_RECENT = 5

        override suspend fun identifyEntityId(
            allEntities: List<Timer>,
            rawText: String,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): IdResolutionResult {
            val timerListText = allEntities.joinToString("\n") {
                "- ${it.label ?: "(no label)"}, duration: ${it.duration}, id=${it._id}"
            }.ifEmpty { "There are no timers" }

            val recentTimer = allEntities.maxByOrNull { it.startedAt }?.takeIf {
                ChronoUnit.MINUTES.between(it.startedAt, Instant.now()) <= MINUTES_TIMER_IS_EXPECTED_RECENT
            }?.let {
                "- ${it.label ?: "(no label)"}, duration: ${it.duration}, id=${it._id}"
            }

            val prompt = buildEntityIdentificationPrompt(
                action = "delete",
                entityName = "timer",
                entityListText = timerListText,
                rawText = rawText,
                lastCreatedEntityDescription = recentTimer,
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
    override suspend fun loadEntities(friendshipId: FriendshipId): List<Timer> {
        return timerRepository.findByFriendshipId(friendshipId)
    }

    override suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: Unit) {
        if (id != null) timerRepository.remove(friendshipId, id)
    }
}