package icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.AppointmentReminder
import icu.neurospicy.fibi.domain.model.FriendshipId
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
class ListAppointmentRemindersSubtaskHandler(
    private val reminderRepository: ReminderRepository,
    private val friendshipLedger: FriendshipLedger,
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
) : SubtaskHandler {
    override fun canHandle(intent: Intent): Boolean = intent == AppointmentReminderIntents.List

    override suspend fun handle(
        subtask: Subtask,
        context: GoalContext,
        friendshipId: FriendshipId
    ): SubtaskResult {
        val rawText = subtask.parameters["rawText"] as? String
        val reminders = reminderRepository.findAppointmentRemindersBy(friendshipId)
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC
        val selectedReminders = if (rawText == null) {
            reminders
        } else {
            val messageTime = context.originalMessage?.receivedAt ?: Instant.now()
            if (reminders.isEmpty()) {
                return SubtaskResult.success("You have no appointment reminders at the moment.", subtask)
            }
            val reminderListText = reminders.joinToString("\n") {
                "- text: ${it.text}, keywords: ${it.matchingTitleKeywords.joinToString()}, offset: ${it.offset}, before: ${it.remindBeforeAppointment}, id=${it._id}"
            }
            val prompt = """
            The user has requested a list or search over their appointment reminders.

            Here is the list of appointment reminders:
            $reminderListText

            User message:
            "$rawText"

            🎯 Your task is to return the IDs of all appointment reminders the user is likely referring to. 
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
            ) ?: return SubtaskResult.failure(
                "Failed to interpret user intent for appointment reminder listing",
                subtask
            )
            val selectedIds = try {
                objectMapper.readTree(response).mapNotNull { it.asText() }
            } catch (e: Exception) {
                emptyList()
            }
            reminders.filter { it._id in selectedIds }
        }
        if (selectedReminders.isEmpty()) {
            return SubtaskResult.success(
                "The friend requested appointment reminders with \"${subtask.description}\". But, no reminders matched their request.",
                subtask
            )
        }
        val resultText = selectedReminders.joinToString("\n") {
            reminderToHumanReadableString(it, timezone)
        }
        return SubtaskResult.success(
            "The friend requested appointment reminders with \"${subtask.description}\". The matching appointment reminders are:\n$resultText\n" +
                    "The result will be shown to the friend. Convert reminder times and offsets to a human-friendly format.",
            subtask
        )
    }

    private fun reminderToHumanReadableString(reminder: AppointmentReminder, timezone: ZoneId): String =
        "- ${reminder.text}: will trigger ${if (reminder.remindBeforeAppointment) "before" else "after"} appointments with keywords [${reminder.matchingTitleKeywords.joinToString()}], offset: ${reminder.offset.toMinutes()} min"

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