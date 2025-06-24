package icu.neurospicy.fibi.domain.service.friends.interaction.timers

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.domain.model.Timer
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TimerRepository
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.interaction.prompt.buildEntityIdentificationPrompt
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Component
class UpdateTimerSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val timerRepository: TimerRepository,
    friendshipLedger: FriendshipLedger,
) : CrudSubtaskHandler<UpdateTimerInformation, Timer>(
    intent = TimerIntents.Update,
    entityHandler = object : CrudEntityHandler<UpdateTimerInformation, Timer> {

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

            val recentTimer = allEntities.maxByOrNull { it.startedAt }?.let {
                "- ${it.label ?: "(no label)"}, duration: ${it.duration}, id=${it._id}"
            }

            val prompt = buildEntityIdentificationPrompt(
                action = "update",
                entityName = "timer",
                entityListText = timerListText,
                rawText = rawText,
                lastCreatedEntityDescription = recentTimer,
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
            previousData: UpdateTimerInformation?,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): ExtractionResult<UpdateTimerInformation> {
            val prompt = """
                You are helping the user update an existing timer.

                A timer has:
                - label: name of the timer
                - duration: how long the timer lasts

                Extract only what the user clearly wants to update. No chat, no explanation.
                Do NOT reuse existing values unless explicitly mentioned.

                Return valid JSON:
                {
                  "duration": "PT15M",
                  "label": "laundry"
                }

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
            val durationText = json["duration"]?.asText()?.takeIf { it.isNotBlank() }
            val label = json["label"]?.asText()?.takeIf { it.isNotBlank() } ?: previousData?.label

            val duration = try {
                durationText?.let { Duration.parse(it) }
            } catch (_: Exception) {
                null
            }

            val resolvedDuration = duration ?: previousData?.duration
            val resolvedLabel = label ?: previousData?.label
            val entity = if (resolvedDuration == null && resolvedLabel == null) null
            else UpdateTimerInformation(duration = resolvedDuration, label = resolvedLabel)

            return ExtractionResult(
                data = entity,
            )
        }
    },
    friendshipLedger
) {
    override suspend fun loadEntities(friendshipId: FriendshipId): List<Timer> {
        return timerRepository.findByFriendshipId(friendshipId)
    }

    override suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: UpdateTimerInformation) {
        if (id != null) {
            timerRepository.update(friendshipId, id, entity.duration, entity.label)
        }
    }
}

data class UpdateTimerInformation(
    val duration: Duration?, val label: String? = null,
)