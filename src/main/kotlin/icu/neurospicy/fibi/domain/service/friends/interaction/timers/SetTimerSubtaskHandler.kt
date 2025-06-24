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
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Component
class SetTimerSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val timerRepository: TimerRepository,
    friendshipLedger: FriendshipLedger,
) : CrudSubtaskHandler<NewTimerInformation, Timer>(
    intent = TimerIntents.Set,
    entityHandler = object : CrudEntityHandler<NewTimerInformation, Timer> {

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
            return NoActionResolutionResult()
        }

        override suspend fun extractEntityData(
            rawText: String,
            previousData: NewTimerInformation?,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): ExtractionResult<NewTimerInformation> {
            val prompt = """
                You are helping the user set a timer.

                A timer needs:
                - duration (required): How long to wait before the timer rings. ISO-8601 duration format or number of minutes.
                - label (optional): What the timer is for.

                This is a multi-turn conversation. You may get partial information. Missing fields will be asked later.

                ✅ Only extract values the user clearly states.
                ❌ Do NOT guess or invent.

                Return valid JSON:
                {
                  "duration": "PT15M",
                  "label": "cook pasta"
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
            } catch (e: Exception) {
                null
            }
            val entity = if (duration == null) null else NewTimerInformation(duration, label)

            return ExtractionResult(
                data = entity,
                missingFields = if (duration == null) listOf("duration") else emptyList(),
            )
        }
    },
    friendshipLedger
) {
    override suspend fun loadEntities(friendshipId: FriendshipId): List<Timer> = emptyList()

    override suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: NewTimerInformation) {
        timerRepository.save(
            Timer(
                owner = friendshipId,
                label = entity.label ?: "",
                duration = entity.duration,
                startedAt = Instant.now()
            )
        )
    }
}

data class NewTimerInformation(
    val duration: Duration, val label: String? = null,
)