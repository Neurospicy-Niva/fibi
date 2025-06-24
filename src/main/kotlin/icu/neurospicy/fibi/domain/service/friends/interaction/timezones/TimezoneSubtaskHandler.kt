package icu.neurospicy.fibi.domain.service.friends.interaction.timezones

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.tools.FriendSettingsTools
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@Component
class TimezoneSubtaskHandler(
    private val friendshipLedger: FriendshipLedger,
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : SubtaskHandler {
    override fun canHandle(intent: Intent): Boolean {
        return intent in TimezoneIntents.AllTimezoneIntents
    }

    override suspend fun handle(
        subtask: Subtask, context: GoalContext, friendshipId: FriendshipId
    ): SubtaskResult {
        val currentTimezone = friendshipLedger.findBy(friendshipId)?.timeZone
        val timezone = currentTimezone ?: ZoneOffset.UTC
        val systemPrompt = """
Its task is to determine the user's most accurate time zone.

You are offered messages of the user which contain information on their timezone, such as, their current time, their timezone, their location.
${if (currentTimezone?.toString()?.isNotBlank() == true) "Current timezone of user: $currentTimezone\n" else ""}

Use tools to set the new timezone.

Return JSON with result:
{
    "success": Boolean
    "timezone": String parsable using ZoneId.of() in kotlin
}
        """.trimIndent()
        return try {
            val json = llmClient.promptReceivingJson(
                listOf(
                    SystemMessage(systemPrompt),
                    org.springframework.ai.chat.messages.UserMessage(subtask.parameters["rawText"].toString())
                ),
                OllamaOptions.builder().model("[MODEL_NAME]").temperature(0.0).topP(0.3).build(),
                timezone,
                context.originalMessage?.receivedAt ?: Instant.now(),
                tools = setOf(FriendSettingsTools(friendshipLedger, friendshipId, applicationEventPublisher))
            )
            val result = objectMapper.readValue(json, TimezoneResult::class.java)
            val newTimezone = ZoneId.of(result.timezone)
            if (result.success == true && newTimezone != null) {
                SubtaskResult.success("The timezone is updated to $newTimezone", updatedSubtask = subtask)
            } else {
                SubtaskResult.success("The timezone is not updated", updatedSubtask = subtask)
            }
        } catch (e: Exception) {
            SubtaskResult.failure("Failed to parse result of timezone extraction: $e", subtask)
        }
    }

    data class TimezoneResult(val success: Boolean?, val timezone: String?)

    override suspend fun tryResolveClarification(
        subtask: Subtask,
        clarificationQuestion: SubtaskClarificationQuestion,
        answer: UserMessage,
        context: GoalContext,
        friendshipId: FriendshipId
    ): SubtaskClarificationResult {
        return SubtaskClarificationResult.success(updatedSubtask = subtask)
    }

}