package icu.neurospicy.fibi.domain.service.friends

import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.*
import icu.neurospicy.fibi.domain.service.friends.tools.CalendarTools
import icu.neurospicy.fibi.domain.service.friends.tools.ChatHistoryTools
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime.now
import java.time.format.DateTimeFormatter

@Service
class CalendarPlanningInteraction(
    private val calendarRepository: CalendarRepository,
    private val friendshipLedger: FriendshipLedger,
    private val calendarConfigurationRepository: CalendarConfigurationRepository,
    private val chatRepository: ChatRepository,
    private val llmClient: LlmClient,
    eventPublisher: ApplicationEventPublisher,
    private val conversationRepository: ConversationRepository,
    private val promptsConfiguration: PromptsConfiguration,
    private val defaultModel: String
) : AbstractInteraction(eventPublisher) {

    override suspend fun processUserRequestWithLlm(
        message: icu.neurospicy.fibi.domain.model.UserMessage, friendshipId: FriendshipId
    ): InteractionResult {
        LOG.info("Processing calendar planning request for friendship: {}", friendshipId)

        val friend = friendshipLedger.findBy(friendshipId)!!
        val timezone = friend.timeZone ?: UTC

        val promptText = createCalendarPlanningPrompt(
            timezone = timezone, friendshipId
        )

        return (llmClient.promptReceivingText((conversationRepository.findByFriendshipId(friendshipId)?.messages?.map { it.toLlmMessage() }
            ?: emptyList()).plus(SystemMessage(promptText)).plus(UserMessage(message.text)),
            OllamaOptions.builder().model(defaultModel).temperature(0.1).numPredict(4096).build(),
            timezone,
            message.receivedAt,
            tools = setOf(
                CalendarTools(calendarRepository, calendarConfigurationRepository, friendshipLedger, friendshipId),
                ChatHistoryTools(friendshipLedger, chatRepository, friendshipId)
            ))?.let { InteractionResult(it) } ?: InteractionResult("No reply by assistant.")).apply {
            LOG.debug("Processed message (id:{}): {}", message.messageId, this.text)
        }
    }

    private fun createCalendarPlanningPrompt(
        timezone: ZoneId, friendshipId: FriendshipId
    ): String {

        val upcomingAppointments = calendarRepository.loadAppointmentsForTimeRange(
            TimeRange(now().minusDays(7).toInstant(), Duration.ofDays(14)), friendshipId
        )

        val currentDateTime = now(timezone)
        val dateFormatter = DateTimeFormatter.ISO_DATE
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val formattedAppointments = upcomingAppointments.joinToString("\n") { appointment ->
            val startDateTime = appointment.startAt.localDate
            val endDateTime = appointment.endAt.localDate

            val formattedDate = dateFormatter.format(startDateTime)
            val formattedStartTime = timeFormatter.format(startDateTime)
            val formattedEndTime = timeFormatter.format(endDateTime)

            "- Date: $formattedDate, Start: $formattedStartTime, End: $formattedEndTime, " + "Title: ${appointment.summary}, ID: ${appointment.appointmentId}"
        }

        return """# **You are a calendar assistant with access to calendar functions**

## Primary Purpose
- USE available tools to manage calendar efficiently
- CALL appropriate functions immediately for calendar requests
- ORGANIZE calendar information clearly
- ONLY use EXISTING appointment data
- The user does not know IDs and shall not know about them. It's your job to determine the ID based on the user's input.

## ⚠️ IMPORTANT
Tools send their own confirmation messages!
Your responses should be BRIEF and WARM, adding personality without duplicating information.

## Context Information
Today: ${dateFormatter.format(currentDateTime)} (ISO-8601)
Local time: ${timeFormatter.format(currentDateTime)}, Timezone: $timezone

### Upcoming's appointments:
${formattedAppointments.ifEmpty { "No upcoming appointments." }}

---
USE appropriate tool functions for calendar operations. After tool execution, provide a SHORT, SUPPORTIVE response in assistants forest fairy style."""
    }


    companion object {
        private val LOG = LoggerFactory.getLogger(CalendarPlanningInteraction::class.java)
    }
}