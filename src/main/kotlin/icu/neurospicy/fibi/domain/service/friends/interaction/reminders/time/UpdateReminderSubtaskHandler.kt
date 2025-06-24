package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.ReminderRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.interaction.prompt.buildEntityIdentificationPrompt
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

@Component
class UpdateReminderSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val reminderRepository: ReminderRepository,
    friendshipLedger: FriendshipLedger,
) : CrudSubtaskHandler<UpdatedReminderInformation, Reminder>(
    intent = ReminderIntents.Update, entityHandler = object : CrudEntityHandler<UpdatedReminderInformation, Reminder> {
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
                action = "update",
                entityName = "reminder",
                entityListText = reminderListText,
                rawText = rawText,
                lastCreatedEntityDescription = recentReminder,
                clarificationQuestion = clarificationQuestion,
                answer = answer
            )

            val resultJson = llmClient.promptReceivingJson(
                listOf(UserMessage(prompt)),
                OllamaOptions.builder().model("[MODEL_NAME]").temperature(0.0).topP(0.8).build(),
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
            previousData: UpdatedReminderInformation?,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): ExtractionResult<UpdatedReminderInformation> {
            val systemPrompt = """You are resolving the parameters of a TIME-BASED REMINDER update.

The user wants to update an existing reminder with a new time or message text.

✅ Extract only what the user clearly wants to change.
❌ Do NOT guess. Do NOT reuse existing values unless stated.

Return valid JSON:
{
  "text": "...", // optional, add if text shall be updated
  "remindAt": "2042-01-21T12:30" // optional, add if remindAt shall be updated
}

❗NO explanation. NO chat. Use FEW TARGETED tool calls!
""".trimIndent()

            val now = messageTime.atZone(timezone)
            val formattedTriggerTime =
                now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.US))
            val dateContext = """
Sent on ${
                now.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() }
            }, $formattedTriggerTime at ${
                now.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.US))
            } (Message timezone: $timezone)
ISO format: $now
""".trimIndent()

            val userPrompt = """Determine what to update in the time-based reminder.

Message:
---
$dateContext
---
$rawText

⛔ You MUST use the message's sending time "$now" to determine relative dates and times!
For simplification, you MAY use local date time format without timezone.
""".trimIndent()
            val resultJson = llmClient.promptReceivingJson(
                listOf(SystemMessage(systemPrompt), UserMessage(userPrompt)),
                OllamaOptions.builder().model("[MODEL_NAME]").temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime
            ) ?: return ExtractionResult()

            val json = objectMapper.readTree(resultJson)
            val remindAtRaw = json["remindAt"]?.asText()
            val remindAt = try {
                when {
                    remindAtRaw == null -> null
                    remindAtRaw.contains("Z") -> ZonedDateTime.parse(remindAtRaw).withZoneSameLocal(timezone)
                        .toLocalDateTime()

                    remindAtRaw.contains("+") -> OffsetDateTime.parse(remindAtRaw).atZoneSimilarLocal(timezone)
                        .toLocalDateTime()

                    else -> LocalDateTime.parse(remindAtRaw)
                }

            } catch (_: Exception) {
                null
            }
            val clarifyingQuestion = if (remindAt?.atZone(timezone)?.toInstant()?.isBefore(Instant.now()) == true) {
                "When do you want to be reminded?"
            } else null

            val text = json["text"]?.asText()?.takeIf { it.isNotBlank() }

            val updatedReminderInformation = UpdatedReminderInformation(text, remindAt)
            return ExtractionResult(
                clarifyingQuestion = clarifyingQuestion,
                data = updatedReminderInformation,
                missingFields = updatedReminderInformation.missingFields
            )
        }
    }, friendshipLedger
) {
    override suspend fun loadEntities(friendshipId: FriendshipId): List<Reminder> {
        return reminderRepository.findTimeBasedRemindersBy(friendshipId)
    }

    override suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: UpdatedReminderInformation) {
        if (id != null && entity.complete) {
            val existing = reminderRepository.findTimeBasedReminderBy(friendshipId, id) ?: return
            reminderRepository.setReminder(
                existing.update(
                    text = entity.text ?: existing.text,
                    trigger = entity.remindAt?.let { DateTimeBasedTrigger(it, existing.trigger.timezone) }
                        ?: existing.trigger))
        }
    }
}

data class UpdatedReminderInformation(
    val text: String?, val remindAt: LocalDateTime?,
) {
    val missingFields: List<String>
        get() = if (complete) emptyList() else ((if (remindAt == null) listOf("remindAt") else emptyList()) + if (text.isNullOrBlank()) listOf(
            "text"
        ) else emptyList())
    val complete: Boolean get() = remindAt != null || text != null
}