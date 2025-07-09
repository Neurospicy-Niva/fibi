package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.ReminderRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

data class NewReminderInformation(
    val text: String?, val remindAt: LocalDateTime?,
)

@Component
class SetReminderSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val reminderRepository: ReminderRepository,
    private val friendshipLedger: FriendshipLedger,
    private val complexTaskModel: String,
) : CrudSubtaskHandler<NewReminderInformation, Reminder>(
    intent = ReminderIntents.Set,
    entityHandler = object : CrudEntityHandler<NewReminderInformation, Reminder> {

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
        ): IdResolutionResult = NoActionResolutionResult()

        override suspend fun extractEntityData(
            rawText: String,
            previousData: NewReminderInformation?,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): ExtractionResult<NewReminderInformation> {
            val systemPrompt = """You are resolving the parameters of a TIME-BASED REMINDER request.
The user wants to be reminded at a specific future time (e.g., "tomorrow at 2pm", "November 20 at 9am", "on Monday").

Your job is determine the future datetime to remind the user at and the reminder text.

Use the tools available to verify which date “next Monday” or “on Friday” refers to.
⛔ You are NOT allowed to guess weekdays or dates yourself!
⚠️ The tools are ALWAYS correct!
⚠️ You MUST directly use dates mentioned in the message.

Output JSON with:
- text: the message to show when reminding
- remindAt: an ISO 8601 datetime, e.g., 2042-01-21T12:30
Only output a valid JSON object with the required fields.
❗ NO explanation. NO chat. Use FEW TARGETED tool calls!""".trimIndent()

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

            val userPrompt = """Determine datetime and text for my reminder.

! NEVER guess weekdays or dates yourself.
But, use dates and times mentioned in the message.

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
                OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime
            ) ?: return ExtractionResult()

            val json = objectMapper.readTree(resultJson)
            val text = json["text"]?.asText()?.takeIf { it.isNotBlank() }
            val remindAtRaw = json["remindAt"]?.asText()

            val remindAt = try {
                remindAtRaw?.let { LocalDateTime.parse(it.removeSuffix("Z")) }
            } catch (_: Exception) {
                null
            }

            val entity = NewReminderInformation(text = text, remindAt = remindAt)

            val clarifyingQuestion =
                if (entity.remindAt?.atZone(timezone)?.toInstant()?.isBefore(Instant.now()) == true) {
                    "When do you want to be reminded?"
                } else null

            return ExtractionResult(
                clarifyingQuestion = clarifyingQuestion,
                data = entity,
                missingFields = buildList {
                    if (text == null) add("text")
                    if (remindAt == null) add("remindAt")
                }
            )
        }
    },
    friendshipLedger
) {
    override suspend fun loadEntities(friendshipId: FriendshipId): List<Reminder> = emptyList()

    override suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: NewReminderInformation) {
        val zoneId = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneId.of("UTC")
        if (entity.remindAt == null || entity.text == null) throw IllegalArgumentException("text and remindAt is mandatory")
        if (entity.remindAt.atZone(zoneId).toInstant()
                .isBefore(Instant.now())
        ) throw IllegalArgumentException("remind at must be in the future")
        reminderRepository.setReminder(
            Reminder(
                owner = friendshipId, trigger = DateTimeBasedTrigger(entity.remindAt, zoneId), text = entity.text
            )
        )
    }
}