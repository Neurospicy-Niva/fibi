package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger

import icu.neurospicy.fibi.domain.service.friends.interaction.RelevantText
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.ZoneId
import org.springframework.ai.chat.messages.UserMessage as AiUserMessage


interface TimeBasedReminderClassifier {
    suspend fun extractAddReminders(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractUpdateReminders(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractRemoveReminders(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractListReminders(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
}

@Component
class TimeBasedReminderClassifierUsingLlm(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val friendshipLedger: FriendshipLedger,
    private val complexTaskModel: String,
) : TimeBasedReminderClassifier {
    override suspend fun extractAddReminders(
        friendshipId: FriendshipId, message: UserMessage,
    ): List<RelevantText> = classify(message, friendshipId, "add")

    override suspend fun extractRemoveReminders(
        friendshipId: FriendshipId, message: UserMessage,
    ): List<RelevantText> = classify(message, friendshipId, "remove")

    override suspend fun extractListReminders(
        friendshipId: FriendshipId, message: UserMessage,
    ): List<RelevantText> = classify(message, friendshipId, "list")

    /**
     * Extracts all segments from the message related to updating a time-based reminder,
     * using a custom LLM prompt.
     */
    override suspend fun extractUpdateReminders(
        friendshipId: FriendshipId, message: UserMessage,
    ): List<RelevantText> {
        val prompt = AiUserMessage(
            """
You are a helpful assistant. Your task is to extract all segments from the following message related to updating a time-based reminder.

A time-based reminder consists of:
- a specific trigger time or date (e.g., "at 3pm tomorrow", "on Friday")
- an optional text to be reminded of (e.g., "call mom", "buy cake")

When a user wants to update such a reminder, they might mention:
- a reference to an existing reminder (e.g., "the reminder about cake")
- new values like new time or updated text

For each update, return:
- relevantText: the complete relevant portion of the message the user wrote
- description: short description of the task from the user's point of view

❗ Make sure to include ALL relevant parts in relevantText – both description of the reminder and the new values.
If it's necessary, you can split the message and recombine parts without altering its contents.

Output must be a JSON array like this:
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
        val options = OllamaOptions.builder().model(complexTaskModel).temperature(0.1).build()
        return llmClient.promptReceivingJson(
            listOf(prompt), options, zone, message.receivedAt
        )?.let { parseRelevantText(it) } ?: emptyList()
    }


    suspend fun classify(message: UserMessage, friendshipId: FriendshipId, action: String): List<RelevantText> {
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneId.of("UTC")
        return llmClient.promptReceivingJson(
            listOf(
                SystemMessage(CLASSIFICATION_SYSTEM_PROMPT), AiUserMessage(buildPrompt(message.text, action))
            ),
            OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.8).build(),
            timezone,
            message.receivedAt
        )?.let { parseRelevantText(it) } ?: emptyList()
    }

    private fun parseRelevantText(json: String): List<RelevantText> {
        return try {
            objectMapper.readValue<ExtractionResult>(json).tasks
        } catch (e: Exception) {
            LOG.warn("Failed to parse reminder tasks from LLM result: ${e.message}")
            emptyList()
        }
    }

    data class ExtractionResult(val tasks: List<RelevantText>)

    private fun buildPrompt(text: String, action: String): String = """
You are a helpful assistant. Your task is to extract all task-related segments of the following message related to "$action".
Only include such tasks.

A reminder triggers at a specific date and/or time,
- It is not tied to an event like a meeting or an appointment
- It is not a countdown or short-term alert like a timer

Respond with a JSON object with one field `tasks`, which is an array of task objects:
For each task, return a short description and the exact relevant text:
- relevantText: the complete portion of the message that refers to the action of the task.
- description: short description of the task from the user's point of view

The relevantText MAY include:
- All date/time expressions
- The text to be reminded of
- Any clarification context if the reminder is being updated or removed
- Text to identify reminders for remove or list actions

❗ Make sure to include ALL relevant parts in relevantText – both description of the reminder and the new values.
If it's necessary, you can split the message and recombine parts without altering its contents.

Example:
{
  "tasks": [
    {
      "description": "Remind me to call my sister tomorrow"
      "relevantText": "Remind me tomorrow at 3pm to call my sister"
    }
  ]
}

User message:
"$text"
    """.trimIndent()


    companion object {
        private const val CLASSIFICATION_SYSTEM_PROMPT = """
            You are a smart assistant classifying a message. Only return tasks for reminders related to the specified action.
        """
        private val LOG = LoggerFactory.getLogger(TimeBasedReminderClassifier::class.java)
    }
}