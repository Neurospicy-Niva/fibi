package icu.neurospicy.fibi.domain.service.friends.interaction.calendar

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.Appointment
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.CalendarConfigurationRepository
import icu.neurospicy.fibi.domain.repository.CalendarRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TimeRange
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.tools.CalendarTools
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.*
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime.now
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

@Component
class ListAppointmentsSubtaskHandler(
    private val calendarRepository: CalendarRepository,
    private val friendshipLedger: FriendshipLedger,
    private val calendarConfigurationRepository: CalendarConfigurationRepository,
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val complexTaskModel: String,
) : SubtaskHandler {

    override fun canHandle(intent: Intent): Boolean = intent == CalendarIntents.ListAppointments

    override suspend fun handle(
        subtask: Subtask, context: GoalContext, friendshipId: FriendshipId,
    ): SubtaskResult {
        val friend =
            friendshipLedger.findBy(friendshipId) ?: return SubtaskResult.failure("Missing friendship data", subtask)
        val timezone = friend.timeZone ?: UTC
        val rawText = subtask.parameters["rawText"] as? String ?: ""
        val category =
            CalendarQueryCategory.entries.firstOrNull {
                it.name.lowercase() == subtask.parameters["queryCategory"].toString().lowercase()
            }
                ?: CalendarQueryCategory.CombinedQuery
        val messageTime = context.originalMessage?.receivedAt ?: Instant.now()

        val now = messageTime.atZone(timezone)
        val formattedTriggerTime = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.US))
        val dateContext = """
Sent on ${
            now.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() }
        }, $formattedTriggerTime at ${
            now.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.US))
        } (Message timezone: $timezone)
ISO format: $now
        """.trimIndent()
        return when (category) {
            CalendarQueryCategory.SpecificTimeRange, CalendarQueryCategory.RelativeTimeRange -> handleTimeRelatedQuery(
                category, rawText, dateContext, timezone, messageTime, subtask, friendshipId
            )

            CalendarQueryCategory.KeywordSearch, CalendarQueryCategory.CombinedQuery -> {
                handleQueryDeterminingIds(
                    buildCombinedQueryPrompt(timezone, friendshipId),
                    timezone,
                    friendshipId,
                    rawText,
                    messageTime,
                    subtask
                )
            }

            CalendarQueryCategory.KeywordInSpecificTimeRange, CalendarQueryCategory.KeywordInRelativeTimeRange -> handleKeywordCombinedWithTimeRelatedQuery(
                category, rawText, dateContext, timezone, messageTime, subtask, friendshipId
            )

            else -> SubtaskResult.failure("Unsupported category: $category", subtask)
        }
    }

    private suspend fun handleKeywordCombinedWithTimeRelatedQuery(
        category: CalendarQueryCategory,
        rawText: String,
        dateContext: String,
        timezone: ZoneId,
        messageTime: Instant,
        subtask: Subtask,
        friendshipId: FriendshipId,
    ): SubtaskResult {
        val prompt =
            if (category == CalendarQueryCategory.SpecificTimeRange) buildSpecificTimeRangePrompt(rawText, dateContext)
            else buildRelativeTimeRangePrompt(rawText, dateContext)
        val appointmentsForTimeRange = try {
            determineAndLoadAppointmentsForTimeRange(friendshipId, prompt, timezone, messageTime)
        } catch (e: Exception) {
            return SubtaskResult.failure(e.message ?: "Failed to determining appointments for time range", subtask)
        }
        if (appointmentsForTimeRange.isEmpty()) {
            return SubtaskResult.success(
                "The friend requested appointments with \"$rawText\". Tell them that no matching appointments were found.",
                subtask
            )
        }
        return handleQueryDeterminingIds(
            buildKeywordInTimeRangeQueryPrompt(timezone, appointmentsForTimeRange),
            timezone,
            friendshipId,
            rawText,
            messageTime,
            subtask
        )
    }

    private suspend fun handleQueryDeterminingIds(
        prompt: String,
        timezone: ZoneId,
        friendshipId: FriendshipId,
        rawText: String,
        messageTime: Instant,
        subtask: Subtask,
    ): SubtaskResult {
        val response = llmClient.promptReceivingText(
            listOf(SystemMessage(prompt), UserMessage(rawText)),
            OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.7).build(),
            timezone,
            messageTime,
            tools = setOf(
                CalendarTools(calendarRepository, calendarConfigurationRepository, friendshipLedger, friendshipId),
                ShortCutCalendarIdsTools(calendarRepository, friendshipId, timezone)
            )
        ) ?: return SubtaskResult.failure("LLM did not respond to appointment selection.", subtask)

        val selectedIds = response.split(",").map { it.trim() }.toSet()

        val selectedAppointments = calendarRepository.loadAppointmentsById(friendshipId, selectedIds)

        return if (selectedAppointments.isEmpty()) SubtaskResult.success(
            "The friend requested appointments with \"${subtask.description}\". Tell them that no matching appointments were found.",
            subtask
        )
        else SubtaskResult.success(
            "The friend requested appointments with \"${rawText}\". The matching appointments are:\n${
                selectedAppointments.joinToString("\n") {
                    appointmentToHumanReadableString(it, timezone)
                }
            }\n", subtask)
    }

    private suspend fun handleTimeRelatedQuery(
        category: CalendarQueryCategory,
        rawText: String,
        dateContext: String,
        timezone: ZoneId,
        messageTime: Instant,
        subtask: Subtask,
        friendshipId: FriendshipId,
    ): SubtaskResult {
        val prompt = if (category == CalendarQueryCategory.KeywordInSpecificTimeRange) buildSpecificTimeRangePrompt(
            rawText, dateContext
        )
        else buildRelativeTimeRangePrompt(rawText, dateContext)
        val appointmentsForTimeRange = try {
            determineAndLoadAppointmentsForTimeRange(friendshipId, prompt, timezone, messageTime)
        } catch (e: Exception) {
            return SubtaskResult.failure(e.message ?: "Failed to determining appointments for time range", subtask)
        }
        if (appointmentsForTimeRange.isEmpty()) {
            return SubtaskResult.success(
                "The friend requested appointments with \"${rawText}\". Tell them that no matching appointments were found.",
                subtask
            )
        }
        val humanReadableAppointments =
            appointmentsForTimeRange.joinToString("\n") { appointmentToHumanReadableString(it, timezone) }
        return SubtaskResult.success(
            "The friend requested appointments with \"${rawText}\". The matching appointments are:\n$humanReadableAppointments\n" + "The result will be shown to the friend. Convert appointments times to a human-friendly format like \"today at 3pm\" or \"next Monday, 10:30am\".",
            subtask
        )
    }

    private suspend fun determineAndLoadAppointmentsForTimeRange(
        friendshipId: FriendshipId, prompt: String, timezone: ZoneId, messageTime: Instant,
    ): List<Appointment> {
        val timeRangeJson = try {
            llmClient.promptReceivingJson(
                listOf(UserMessage(prompt)),
                OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime
            ) ?: throw Exception("Failed to resolve specific time range.")
        } catch (e: Exception) {
            throw Exception("Failed to resolve specific time range.")
        }

        val start: Instant
        val end: Instant
        try {
            start = objectMapper.readTree(timeRangeJson).get("start")?.asText()?.let { parseDateTime(it, timezone) }!!
            end = objectMapper.readTree(timeRangeJson).get("end")?.asText()?.let { parseDateTime(it, timezone) }!!
        } catch (e: Exception) {
            throw Exception("Failed to extract start and end from $timeRangeJson")
        }
        if (end.isBefore(start)) {
            throw Exception("Invalid time range received from LLM")
        }
        return calendarRepository.loadAppointmentsForTimeRange(
            TimeRange(
                start, Duration.between(start, end)
            ), friendshipId
        )
    }

    private fun parseDateTime(dateTimeString: String, timezone: ZoneId): Instant {
        try {
            return Instant.parse(dateTimeString)
        } catch (e: Exception) {
        }

        try {
            return LocalDateTime.parse(dateTimeString).atZone(timezone).toInstant()
        } catch (e: Exception) {
        }

        try {
            return ZonedDateTime.parse(dateTimeString).toInstant()
        } catch (e: Exception) {
        }

        try {
            return OffsetDateTime.parse(dateTimeString).toInstant()
        } catch (e: Exception) {
        }
        throw IllegalArgumentException("Cannot parse date time string $dateTimeString")
    }

    // --- Prompt builders for each category ---

    private fun buildSpecificTimeRangePrompt(
        rawText: String, dateContext: String,
    ): String {
        return """
You are helping to understand a calendar-related user request.

The user requested appointments for a certain time range.
Your task is to determine the time range based on the user message and return start and end.
Your output will be used to search calendar appointments in the mentioned time range.

User message:
---
$dateContext
---
"$rawText"
---

The user expresses time in their local time.

Return a JSON object like:
{
  "start": "2012-04-01T08:00:00",
   "end": "2012-04-02T08:00:00"
}

Return nulls if you're unsure. No chat, no explanation.
""".trimIndent()
    }

    private fun buildRelativeTimeRangePrompt(
        rawText: String, dateContext: String,
    ): String {
        return """
You are helping to resolve a relative time expression from a user calendar request.

The user requested appointments for a certain time range.
Your task is to determine the time range based on the user message and return start and end.
Your output will be used to search calendar appointments in the mentioned time range.

User message:
---
$dateContext
---
"$rawText"
---

The user expresses time in their local time.

Your goal:
- Interpret the user’s time reference (e.g., "tomorrow", "next Monday", "in 5 days")
- Convert it into a concrete UTC time range

Return a JSON object like:
{
  "start": "2012-04-01T08:00:00",
   "end": "2012-04-02T08:00:00"
}

Return nulls if you're unsure. No chat, no explanation.
""".trimIndent()
    }

    private fun buildCombinedQueryPrompt(
        timezone: ZoneId, friendshipId: FriendshipId,
    ): String {
        val upcomingAppointments = calendarRepository.loadAppointmentsForTimeRange(
            TimeRange(now().minusMonths(3).toInstant(), Duration.ofDays(30 * 6)), friendshipId
        )
        val currentDateTime = now(timezone)
        val formattedAppointments = formatAppointmentsForPrompt(upcomingAppointments)

        val appointmentText =
            "The user request might be vague and refer to \"when did I last...\" or \"when is ... upcoming\". We consider 3 month a good range for these types of requests. These are the appointments in this range:\n" + formattedAppointments.ifEmpty { "No upcoming appointments." }
        return buildPromptToDetermineIds(currentDateTime, timezone, appointmentText)
    }

    private fun buildKeywordInTimeRangeQueryPrompt(
        timezone: ZoneId, appointments: List<Appointment>,
    ): String {
        val currentDateTime = now(timezone)
        val formattedAppointments = formatAppointmentsForPrompt(appointments)

        val appointmentText =
            "The user requested appointments of a special kind in a specific time range. These are the appointments in this range:\n" + formattedAppointments.ifEmpty { "No upcoming appointments." }
        return buildPromptToDetermineIds(currentDateTime, timezone, appointmentText)
    }

    private fun buildPromptToDetermineIds(
        currentDateTime: ZonedDateTime?, timezone: ZoneId, appointmentText: String,
    ): String {
        val isoDateFormatter = DateTimeFormatter.ISO_DATE
        val simpleTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val friendsToday = isoDateFormatter.format(currentDateTime)
        val friendsLocalTime = simpleTimeFormatter.format(currentDateTime)

        return """# You are helping to resolve a user calendar request.

Your task is to determine the IDs of appointments the user requested.
Your output will be used to load the appointments and return them to the user.

‼️Always use tools to easily answer typical requests

## Primary Purpose
- USE available tools to manage calendar efficiently
- CALL appropriate functions immediately for calendar requests
- ONLY use EXISTING appointment data
- The user does not know IDs and shall not know about them. It's your job to determine the ID based on the user's input.

## Context Information
Today: $friendsToday (ISO-8601)
Local time: $friendsLocalTime, Timezone: $timezone

### Appointments
$appointmentText

## Answer
Return IDs of matching appointments comma separated like:
id1,id2,...

No explanation, no chat!"""
    }

    private fun formatAppointmentsForPrompt(upcomingAppointments: List<Appointment>): String {
        val dateFormatter = DateTimeFormatter.ISO_DATE
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val formattedAppointments = upcomingAppointments.joinToString("\n") { appointment ->
            val startDateTime = appointment.startAt.instant.atZone(appointment.startAt.zoneId)
            val endDateTime = appointment.endAt.instant.atZone(appointment.endAt.zoneId)

            val formattedDate = dateFormatter.format(startDateTime).removeSuffix("Z")
            val formattedStartTime = timeFormatter.format(startDateTime)
            val formattedEndTime = timeFormatter.format(endDateTime)

            "- Date: $formattedDate, Start: $formattedStartTime, End: $formattedEndTime, " + "Title: ${appointment.summary}, ID: ${appointment._id}"
        }
        return formattedAppointments
    }


    override suspend fun tryResolveClarification(
        subtask: Subtask,
        clarificationQuestion: SubtaskClarificationQuestion,
        answer: icu.neurospicy.fibi.domain.model.UserMessage,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskClarificationResult {
        return SubtaskClarificationResult.success(updatedSubtask = subtask)
    }

    private fun appointmentToHumanReadableString(appointment: Appointment, timezone: ZoneId): String {
        val start = appointment.startAt.instant.atZone(appointment.startAt.zoneId).withZoneSameInstant(timezone)
        val end = appointment.endAt.instant.atZone(appointment.endAt.zoneId).withZoneSameInstant(timezone)
        return "- ${appointment.summary}: from $start to $end"
    }
}