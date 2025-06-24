package icu.neurospicy.fibi.domain.service.friends.interaction.timers

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Timer
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TimerRepository
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
class ListTimersSubtaskHandler(
    private val timerRepository: TimerRepository,
    private val friendshipLedger: FriendshipLedger,
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
) : SubtaskHandler {
    override fun canHandle(intent: Intent): Boolean = intent == TimerIntents.List

    override suspend fun handle(
        subtask: Subtask,
        context: GoalContext,
        friendshipId: FriendshipId
    ): SubtaskResult {
        val rawText = subtask.parameters["rawText"] as? String
        val timers = timerRepository.findByFriendshipId(friendshipId)
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC
        val selectedTimers = if (rawText == null) {
            timers
        } else {
            val messageTime = context.originalMessage?.receivedAt ?: Instant.now()

            if (timers.isEmpty()) {
                return SubtaskResult.success("Inform the user they have no active timers.", subtask)
            }

            val timerListText = timers.joinToString("\n") {
                "- label: ${it.label ?: "(none)"}, duration: ${it.duration}, startedAt: ${it.startedAt}, id=${it._id}"
            }

            val prompt = """
            The user has requested a list or search over their active timers.

            Here is the list of timers:
            $timerListText

            User message:
            "$rawText"

            🎯 Your task is to return the IDs of all timers the user is likely referring to. 
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
            ) ?: return SubtaskResult.failure("Failed to interpret user intent for timer listing", subtask)

            val selectedIds = try {
                objectMapper.readTree(response).mapNotNull { it.asText() }
            } catch (e: Exception) {
                emptyList()
            }

            timers.filter { it._id in selectedIds }
        }

        if (selectedTimers.isEmpty()) {
            return SubtaskResult.success("No timers matched the user's request.", subtask)
        }

        val resultText = selectedTimers.joinToString("\n") {
            timerToHumanReadableString(it, timezone)
        }

        return SubtaskResult.success(
            "The user requested timers with \"${subtask.description}\". The timers are:\n$resultText",
            subtask
        )
    }

    private fun timerToHumanReadableString(timer: Timer, timezone: ZoneId): String =
        "- ${timer.label ?: "(no label)"}: duration ${timer.duration}, will end at ${
            timer.startedAt.plus(
                timer.duration
            ).atZone(timezone)
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