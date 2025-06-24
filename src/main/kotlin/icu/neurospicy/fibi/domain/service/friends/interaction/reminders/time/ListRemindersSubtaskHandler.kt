package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Reminder
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.ReminderRepository
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@Component
class ListRemindersSubtaskHandler(
    private val reminderRepository: ReminderRepository,
    private val friendshipLedger: FriendshipLedger,
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
) : SubtaskHandler {
    override fun canHandle(intent: Intent): Boolean = intent == ReminderIntents.List

    override suspend fun handle(
        subtask: Subtask,
        context: GoalContext,
        friendshipId: FriendshipId
    ): SubtaskResult {
        val rawText = subtask.parameters["rawText"] as? String
        val reminders = reminderRepository.findTimeBasedRemindersBy(friendshipId)
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC
        val selectedReminders = if (rawText == null) {
            reminders
        } else {
            val messageTime = context.originalMessage?.receivedAt ?: Instant.now()

            if (reminders.isEmpty()) {
                return SubtaskResult.success("You have no reminders at the moment.", subtask)
            }

            val reminderListText = reminders.joinToString("\n") {
                "- text: ${it.text}, remindAt: ${it.trigger.localTime} (${it.trigger.timezone}), id=${it._id}"
            }

            val prompt = """
            The user has requested a list or search over their reminders.

            Here is the list of reminders:
            $reminderListText

            User message:
            "$rawText"

            🎯 Your task is to return the IDs of all reminders the user is likely referring to. 
            If the user requests a general listing, return all.
            ⚠️ Don't invent or rephrase content, don't explain, just match intent and return a JSON list of IDs.

            Return a JSON array like:
            ["id1", "id2", ...]
        """.trimIndent()

            val response = llmClient.promptReceivingJson(
                listOf(UserMessage(prompt)),
                OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.8).build(),
                timezone,
                messageTime
            ) ?: return SubtaskResult.failure("Failed to interpret user intent for reminder listing", subtask)

            val selectedIds = try {
                objectMapper.readTree(response).mapNotNull { it.asText() }
            } catch (e: Exception) {
                emptyList()
            }

            reminders.filter { it._id in selectedIds }
        }

        if (selectedReminders.isEmpty()) {
            return SubtaskResult.success(
                "The friend requested reminders with \"${subtask.description}\". But, no reminders matched their request.",
                subtask
            )
        }

        val resultText = selectedReminders.joinToString("\n") {
            reminderToHumanReadableString(it, timezone)
        }

        return SubtaskResult.success(
            "The friend requested reminders with \"${subtask.description}\". The matching reminders are:\n$resultText\n" +
                    "The result will be shown to the friend. Convert reminder times to a human-friendly format like \"today at 3pm\" or \"next Monday, 10:30am\".",
            subtask
        )
    }

    private fun reminderToHumanReadableString(reminder: Reminder, timezone: ZoneId): String =
        "- ${reminder.text}: will trigger at ${
            reminder.trigger.localTime.atZone(reminder.trigger.timezone).withZoneSameInstant(timezone)
        }"

    override suspend fun tryResolveClarification(
        subtask: Subtask,
        clarificationQuestion: SubtaskClarificationQuestion,
        answer: icu.neurospicy.fibi.domain.model.UserMessage,
        context: GoalContext,
        friendshipId: FriendshipId
    ): SubtaskClarificationResult {
        return SubtaskClarificationResult.success(updatedSubtask = subtask)
    }
}