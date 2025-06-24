package icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.AppointmentReminder
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.ReminderRepository
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.interaction.prompt.buildEntityIdentificationPrompt
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class UpdatedAppointmentReminderInformation(
    val text: String? = null,
    val keywords: Set<String>? = null,
    val offsetMinutes: Long? = null,
    val remindBefore: Boolean? = null,
)

@Component
class UpdateAppointmentReminderSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val reminderRepository: ReminderRepository,
    friendshipLedger: FriendshipLedger,
) : CrudSubtaskHandler<UpdatedAppointmentReminderInformation, AppointmentReminder>(
    intent = AppointmentReminderIntents.Update,
    entityHandler = object : CrudEntityHandler<UpdatedAppointmentReminderInformation, AppointmentReminder> {
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
                "- ${it.text}, keywords: ${it.matchingTitleKeywords.joinToString()}, offset: ${it.offset}, before: ${it.remindBeforeAppointment}, id=${it._id}"
            }
            val recentReminder = allEntities.maxByOrNull { it.createdAt }?.let {
                "- ${it.text}, keywords: ${it.matchingTitleKeywords.joinToString()}, offset: ${it.offset}, before: ${it.remindBeforeAppointment}, id=${it._id}"
            }
            val prompt = buildEntityIdentificationPrompt(
                action = "update",
                entityName = "appointment reminder",
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
            previousData: UpdatedAppointmentReminderInformation?,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): ExtractionResult<UpdatedAppointmentReminderInformation> {
            // 1. Extract offsetMinutes and remindBefore
            val systemPromptOffset = """
You are extracting updated timing parameters for an appointment reminder.
The user is sending a message to update a reminder for specific appointments. It's your task to determine when in advance (before) or after an appointment the user wants to be notified.

From the user's message, extract only:
- offsetMinutes: How many minutes before/after to trigger the reminder. Accept integers or ISO 8601 durations (e.g., PT15M).
- remindBefore: true if the reminder should trigger before the appointment, false if after.

Ignore any information that simply describes the existing reminder.

Output only valid JSON:
{
  "offsetMinutes": Integer/ISO8601 durations (e.g., PT15M),
  "remindBeforeAppointment": true/false
}

If the user does NOT clearly intend to update these values, omit them.
Do not include any other information or explanation.
            """.trimIndent()
            val userPromptOffset = """
User message:
---
$rawText
            """.trimIndent()
            val jsonOffset = llmClient.promptReceivingJson(
                listOf(SystemMessage(systemPromptOffset), UserMessage(userPromptOffset)),
                OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime
            )
            val offsetMinutes: Long? = jsonOffset?.let {
                val node = objectMapper.readTree(it)
                node["offsetMinutes"]?.let { offsetNode ->
                    val textValue = offsetNode.asText(null)
                    if (textValue != null) {
                        try {
                            if (textValue.startsWith("P") || textValue.startsWith("PT")) {
                                val duration = Duration.parse(textValue)
                                duration.toMinutes()
                            } else {
                                offsetNode.asLong()
                            }
                        } catch (_: Exception) {
                            offsetNode.asLong()
                        }
                    } else null
                }
            } ?: previousData?.offsetMinutes
            val remindBefore: Boolean? = jsonOffset?.let {
                val node = objectMapper.readTree(it)
                node["remindBeforeAppointment"]?.asBoolean()
            } ?: previousData?.remindBefore

            // 2. Extract the notification text (command style)
            val systemPromptText = """
You are extracting the updated notification text for an appointment reminder.
The user is sending a message to update a reminder for specific appointments. It's your task to determine a short command-style text that will be sent when notifying the user.

From the user's message, extract only the updated short imperative or command sentence.

Ignore any information that just describes the current reminder.

Output only valid JSON:
{
  "text": "Take your pills now"
}

Examples:
User: "Update my reminder before school to say: Bring homework!" → text: "Bring homework!"
User: "Change the message for reminders after school pickup: Ask about homework" → text: "Ask about homework"

If no suitable text is found, omit the field.
Do not include any other information or explanation.
            """.trimIndent()
            val userPromptText = """
User message:
---
$rawText
            """.trimIndent()
            val jsonText = llmClient.promptReceivingJson(
                listOf(SystemMessage(systemPromptText), UserMessage(userPromptText)),
                OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime
            )
            val text: String? = jsonText?.let {
                val node = objectMapper.readTree(it)
                node["text"]?.asText()?.takeIf { it.isNotBlank() }
            } ?: previousData?.text

            // 3. Extract appointment title keywords
            val systemPromptKeywords = """
You are extracting updated keywords that shall match appointment titles.
The user is sending a message to update a reminder for specific appointments. It's your task to find keywords, that exactly match the appointments the user clearly wants to be notified for.

They set of keywords matches an appointment, if any keyword appears in the appointment's title.

Ignore any part of the message that describes the old reminder.

Instructions:
✅ Prefer exact words found in the appointment titles. ALWAYS use singular over plural if not clearly mentioned otherwise.
✅ If the user explicitly names phrases (e.g., "appointments containing 'Special:'"), extract those directly.
✅ If no suitable match is found, you may infer concise, general keywords that likely matches future appointments.
❌ NEVER include the reminder action or message (e.g., "homework") as a keyword unless it's clearly part of the appointment title.

Output valid JSON:
{
  "keywords": ["keyword1", "keyword2"]
}

If no suitable keywords are found, omit the field.
Do not include any other information or explanation.
""".trimIndent()
            val userPromptKeywords = """
User message:
$rawText
            """.trimIndent()
            val jsonKeywords = llmClient.promptReceivingJson(
                listOf(SystemMessage(systemPromptKeywords), UserMessage(userPromptKeywords)),
                OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime
            )
            val keywords: Set<String>? = jsonKeywords?.let {
                val node = objectMapper.readTree(it)
                val arr = node["keywords"]
                if (arr != null && arr.isArray) {
                    val list = arr.mapNotNull { it.asText().takeIf { it.isNotBlank() } }
                    if (list.isNotEmpty()) list.toSet() else null
                } else null
            } ?: previousData?.keywords

            val data = UpdatedAppointmentReminderInformation(
                text = text,
                keywords = keywords,
                offsetMinutes = offsetMinutes,
                remindBefore = remindBefore
            )
            val missing = buildList {
                if (text == null && keywords.isNullOrEmpty() && offsetMinutes == null && remindBefore == null) {
                    add("At least one field to update")
                }
            }
            val clarification = if (missing.isNotEmpty()) {
                "What do you want to update in your appointment reminder?"
            } else null
            return ExtractionResult(
                clarifyingQuestion = clarification,
                data = if (missing.isEmpty()) data else null,
                missingFields = missing
            )
        }
    },
    friendshipLedger
) {
    override suspend fun loadEntities(friendshipId: FriendshipId): List<AppointmentReminder> {
        return reminderRepository.findAppointmentRemindersBy(friendshipId)
    }

    override suspend fun applyUpdate(
        friendshipId: FriendshipId,
        id: String?,
        entity: UpdatedAppointmentReminderInformation,
    ) {
        if (id != null) {
            val existing = reminderRepository.findAppointmentReminderBy(friendshipId, id) ?: return
            val updated = existing.copy(
                text = entity.text ?: existing.text,
                matchingTitleKeywords = entity.keywords ?: existing.matchingTitleKeywords,
                offset = entity.offsetMinutes?.let { Duration.ofMinutes(it) } ?: existing.offset,
                remindBeforeAppointment = entity.remindBefore ?: existing.remindBeforeAppointment
            )
            reminderRepository.setReminder(updated)
        }
    }
}