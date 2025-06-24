package icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.RelevantText
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.ZoneId
import org.springframework.ai.chat.messages.UserMessage as AiUserMessage

interface AppointmentReminderClassifier {
    suspend fun extractSetReminders(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractUpdateReminders(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractRemoveReminders(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractListReminders(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
}

@Component
class AppointmentReminderClassifierUsingLlm(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val friendshipLedger: FriendshipLedger
) : AppointmentReminderClassifier {

    override suspend fun extractSetReminders(
        friendshipId: FriendshipId, message: UserMessage
    ): List<RelevantText> = classify(message, friendshipId, "set")

    override suspend fun extractRemoveReminders(
        friendshipId: FriendshipId, message: UserMessage
    ): List<RelevantText> = classify(message, friendshipId, "remove")

    override suspend fun extractListReminders(
        friendshipId: FriendshipId, message: UserMessage
    ): List<RelevantText> = extractListRemindersSpecial(friendshipId, message)

    /**
     * Extracts all segments from the message related to updating an appointment-based reminder,
     * using a custom LLM prompt.
     */
    override suspend fun extractUpdateReminders(
        friendshipId: FriendshipId, message: UserMessage
    ): List<RelevantText> = extractUpdateRemindersSpecial(friendshipId, message)

    /**
     * Generic classifier for set and remove actions, and as fallback for others.
     */
    suspend fun classify(
        message: UserMessage,
        friendshipId: FriendshipId,
        action: String
    ): List<RelevantText> {
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneId.of("UTC")
        return llmClient.promptReceivingJson(
            listOf(
                SystemMessage(CLASSIFICATION_SYSTEM_PROMPT),
                AiUserMessage(buildPrompt(message.text, action))
            ),
            OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.8).build(),
            timezone,
            message.receivedAt
        )?.let { parseRelevantText(it) } ?: emptyList()
    }

    /**
     * Specialized extraction for update appointment reminders.
     */
    private suspend fun extractUpdateRemindersSpecial(
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<RelevantText> {
        val prompt = AiUserMessage(
            """
You are a helpful assistant. Your task is to extract all segments from the following message related to updating an appointment-based reminder.

An appointment-based reminder consists of:
- is tied to a calendar appointment by keyword(s) in the title (e.g., "doctor", "team meeting")
- has an offset (e.g., "10 minutes before", "5 mins after") relative to the appointment time
- may include a message text to display when triggered

When a user wants to update such a reminder, they might mention:
- a reference to an existing reminder (e.g., "the reminder for dentist")
- new values like new offset, keywords, or updated text

For each update, return:
- relevantText: the complete relevant portion of the message the user wrote
- description: short description of the task from the user's point of view

❗ Make sure to include ALL relevant parts in relevantText – both description of the reminder and the new values.
If it's necessary, you can split the message and recombine parts without altering its contents.

Output must be a JSON object like this:
{
  "tasks": [
    {
      "relevantText": "...",
      "description": "..."
    }
  ]
}

Message:
"${message.text}"
            """.trimIndent()
        )
        val zone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneId.of("UTC")
        val options = OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.1).build()
        return llmClient.promptReceivingJson(
            listOf(prompt), options, zone, message.receivedAt
        )?.let { parseRelevantText(it) } ?: emptyList()
    }

    /**
     * Specialized extraction for listing appointment reminders.
     */
    private suspend fun extractListRemindersSpecial(
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<RelevantText> {
        val prompt = AiUserMessage(
            """
You are a helpful assistant. Extract all segments from the following message related to listing appointment-based reminders.

An appointment-based reminder:
- Is tied to a calendar appointment by keywords in the title
- Has an offset before/after the appointment time

For each request to list reminders, return:
- relevantText: the full portion of the user’s message relevant to listing reminders
- description: a short user-facing description of what is being asked

Respond as JSON:
{
  "tasks": [
    {
      "relevantText": "...",
      "description": "..."
    }
  ]
}

Message:
"${message.text}"
            """.trimIndent()
        )
        val zone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneId.of("UTC")
        val options = OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.1).build()
        return llmClient.promptReceivingJson(
            listOf(prompt), options, zone, message.receivedAt
        )?.let { parseRelevantText(it) } ?: emptyList()
    }

    private fun parseRelevantText(json: String): List<RelevantText> {
        return try {
            objectMapper.readValue<ExtractionResult>(json).tasks
        } catch (e: Exception) {
            LOG.warn("Failed to parse appointment reminder tasks from LLM result: ${e.message}")
            emptyList()
        }
    }

    data class ExtractionResult(val tasks: List<RelevantText>)

    private fun buildPrompt(text: String, action: String): String = """
You are a helpful assistant. Your task is to extract all task-related segments of the following message related to "$action" appointment-based reminders.
Only include such tasks.

An appointment-based reminder:
- Is tied to a calendar appointment by keywords in the title (e.g., "doctor", "meeting")
- Has an offset (e.g., "10 minutes before", "5 mins after") relative to the appointment time
- May include a message text to display when triggered

It is NOT a time-based reminder for a specific date/time, and NOT a timer.

Respond with a JSON object with one field `tasks`, which is an array of task objects:
For each task, return a short description and the exact relevant text:
- relevantText: the complete portion of the message that refers to the action of the task.
- description: short description of the task from the user's point of view

The relevantText MAY include:
- All keyword(s) for appointment matching
- The offset (before/after)
- The text to be reminded of
- Any clarification context if the reminder is being updated or removed
- Text to identify reminders for remove or list actions

❗ Make sure to include ALL relevant parts in relevantText – both description of the reminder and the new values.
If it's necessary, you can split the message and recombine parts without altering its contents.

Example:
{
  "tasks": [
    {
      "description": "Remind me 10 minutes before my dentist appointment",
      "relevantText": "Remind me 10 minutes before any appointment with 'dentist' in the title"
    }
  ]
}

User message:
"$text"
    """.trimIndent()

    companion object {
        private const val CLASSIFICATION_SYSTEM_PROMPT = """
            You are a smart assistant classifying a message. Only return tasks for appointment-based reminders related to the specified action.
        """
        private val LOG = LoggerFactory.getLogger(AppointmentReminderClassifier::class.java)
    }
}