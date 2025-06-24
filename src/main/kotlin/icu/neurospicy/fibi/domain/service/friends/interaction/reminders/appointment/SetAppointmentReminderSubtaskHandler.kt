package icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.AppointmentReminder
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.domain.repository.CalendarRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.ReminderRepository
import icu.neurospicy.fibi.domain.repository.TimeRange
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class NewAppointmentReminderInformation(
    val text: String? = null,
    val keywords: Set<String>? = null,
    val offsetMinutes: Long? = null,
    val remindBefore: Boolean? = null,
)

@Component
class SetAppointmentReminderSubtaskHandler(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val reminderRepository: ReminderRepository,
    friendshipLedger: FriendshipLedger,
    private val calendarRepository: CalendarRepository,
) : CrudSubtaskHandler<NewAppointmentReminderInformation, AppointmentReminder>(
    intent = AppointmentReminderIntents.Set,
    entityHandler = object : CrudEntityHandler<NewAppointmentReminderInformation, AppointmentReminder> {
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
        ): IdResolutionResult = NoActionResolutionResult()

        override suspend fun extractEntityData(
            rawText: String,
            previousData: NewAppointmentReminderInformation?,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): ExtractionResult<NewAppointmentReminderInformation> {
            val appointments = calendarRepository.loadAppointmentsForTimeRange(
                TimeRange(Instant.now().minus(7, ChronoUnit.DAYS), Duration.ofDays(14)), friendshipId
            )
            val upcomingSummaries = appointments.map { it.summary }.distinct().take(40)
            val appointmentList =
                if (upcomingSummaries.isEmpty()) "The user has no appointments." else upcomingSummaries.joinToString("\n") { "- \"$it\"" }

            // 1. Extract offsetMinutes and remindBefore
            val systemPromptOffset = """
You are extracting timing parameters for an appointment reminder.
The user is sending a message to add a reminder for specific appointments. It's your task to determine when in advance (before) or after an appointment the user wants to be notified.

From the user's message, extract only:
- offsetMinutes: How many minutes before/after to trigger the reminder. Accept integers or ISO 8601 durations (e.g., PT15M). Default is 15 if not specified.
- remindBefore: true if the reminder should trigger before the appointment, false if after.

Output only valid JSON:
{
  "offsetMinutes": Integer/ISO8601 durations (e.g., PT15M).
  "remindBeforeAppointment": true/false
}

If the user does clearly intents to specify these values, omit them.
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
                            // Try parse as ISO 8601 duration
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
                } ?: null
            } ?: previousData?.offsetMinutes

            val remindBefore: Boolean? = jsonOffset?.let {
                val node = objectMapper.readTree(it)
                node["remindBeforeAppointment"]?.asBoolean()
            } ?: previousData?.remindBefore

            // 2. Extract the final notification text (command style)
            val systemPromptText = """
You are extracting the exact notification text for an appointment reminder.
The user is sending a message to add a reminder for specific appointments. It's your task to determine a text that is send when notifying the user.

From the user's message, extract only the short command-style text that will be sent as the reminder notification.
It should be a short imperative or command sentence, not a description.

Output only valid JSON:
{
  "text": "Take your pills now"
}

Examples:
User: "Remind me before going to school to take my pills", text: "Take your pills now"
User: "After kindergarten pick up remind me to add tasks and appointments", text: "Add tasks and appointments"
User: "Remind me to pick up prescriptions after doctor appointments", "Pick up prescriptions!"

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

            // 3. Extract appointment title keywords using the upcoming appointments list
            val systemPromptKeywords = """
You are extracting keywords that shall match appointment titles.
The user is sending a message to add a reminder for specific appointments. It's your task to find keywords, that exactly match the appointments, the user clearly wants to be notified.
They set of keywords matches and appointment, if any keyword appears in the appointment's title.

Use the following upcoming appointments to inform your selection:
$appointmentList

Instructions:
✅ Prefer exact words found in the appointment titles. ALWAYS Use singular over plural if not clearly mentioned otherwise.
✅ If the user explicitly names phrases (e.g., "appointments containing 'Special:'"), extract those directly.
✅ If no suitable match is found, you may infer concise, general keywords that likely matches future appointments.
❌ NEVER include the reminder action or message (e.g., "homework") as a keyword unless it's clearly part of the appointment title.

Output valid JSON:
{
  "keywords": ["keyword1", "keyword2"],
}

If no suitable keywords are found, omit the field.
Do not include any other information or explanation.
""".trimIndent()

            val userPromptKeywords =
                """Extract keywords that refer to appointments for which the user wants to be notified.
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

            val data = NewAppointmentReminderInformation(
                text = text, keywords = keywords, offsetMinutes = offsetMinutes, remindBefore = remindBefore
            )

            val missing = buildList {
                if (text == null) add("text")
                if (keywords.isNullOrEmpty()) add("keywords")
                if (remindBefore == null) add("remindBefore")
            }

            val clarification = when {
                text == null -> "What should I remind you about? Please provide a short command."
                keywords.isNullOrEmpty() -> "What kind of appointments should trigger this reminder? Please specify keywords matching appointment titles."
                remindBefore == null -> "Should I remind you before or after the appointment?"
                else -> null
            }

            val message = if (!keywords.isNullOrEmpty()) {
                "Suggesting keywords:\n${keywords.joinToString()}\nwhich matches appointments:\n${
                    appointments.filter { appointment ->
                        keywords.any { keyword ->
                            appointment.summary.contains(
                                keyword
                            )
                        }
                    }.joinToString { "\"${it.summary.take(20)}\"" }.ifBlank { "None yet \uD83D\uDE33" }
                }"
            } else null

            return ExtractionResult(
                responseMessage = message,
                data = if (missing.isEmpty()) data else null,
                missingFields = missing,
                clarifyingQuestion = clarification
            )
        }
    },
    friendshipLedger
) {
    override suspend fun loadEntities(friendshipId: FriendshipId): List<AppointmentReminder> = emptyList()

    override suspend fun applyUpdate(
        friendshipId: FriendshipId, id: String?, entity: NewAppointmentReminderInformation,
    ) {
        if (entity.text == null || entity.keywords == null || entity.remindBefore == null) {
            throw IllegalArgumentException("Incomplete reminder info")
        }

        val offset = Duration.ofMinutes(entity.offsetMinutes ?: 15)
        reminderRepository.setReminder(
            AppointmentReminder(
                owner = friendshipId,
                matchingTitleKeywords = entity.keywords,
                text = entity.text,
                offset = offset,
                remindBeforeAppointment = entity.remindBefore
            )
        )
    }

    override fun getDefaultDataQuestion(): String = "What should I remind you about?"

}