package icu.neurospicy.fibi.domain.service.friends.interaction.timers

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger

import icu.neurospicy.fibi.domain.service.friends.interaction.RelevantText
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.ZoneId
import org.springframework.ai.chat.messages.UserMessage as AiUserMessage

interface TimerClassifier {
    suspend fun extractSetTimers(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractUpdateTimers(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractRemoveTimers(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractListTimers(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
}

@Component
class LlmTimerClassifier(
    private val llmClient: LlmClient,
    private val friendshipLedger: FriendshipLedger,
    private val objectMapper: ObjectMapper,
    private val complexTaskModel: String,
) : TimerClassifier {

    private val options = OllamaOptions.builder().model(complexTaskModel).temperature(0.1).build()

    override suspend fun extractSetTimers(friendshipId: FriendshipId, message: UserMessage): List<RelevantText> {
        return extractTimers("set", friendshipId, message)
    }

    override suspend fun extractUpdateTimers(friendshipId: FriendshipId, message: UserMessage): List<RelevantText> {
        val updatePrompt = AiUserMessage(
            """
                You are a helpful assistant. Your task is to extract all timer-related segments from the following message related to updating a timer.

                A timer to update can be identified by:
                - Its description or label (e.g., "for pizza", "called Tea")
                - Previously set context (such as its purpose)

                The user likely wants to change the label, or trigger time of the timer.

                For each mentioned timer update, return:
                - relevantText: the complete portion of the message that refers to updating the timer
                - description: short description of the task from the user's point of view
                
                If it's necessary, you can split the message and recombine parts without altering its contents.

                Output a JSON array like this:
                [
                  {
                    "relevantText": "...",
                    "description": "..."
                  }
                ]

                Message:
                "${message.text}"
                """.trimIndent()
        )

        val zone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneId.of("UTC")
        val result = llmClient.promptReceivingJson(listOf(updatePrompt), options, zone, message.receivedAt)

        return result?.let { json ->
            try {
                val parsed = objectMapper.readTree(json)
                parsed.mapNotNull {
                    val relevantText = it["relevantText"].asText("")
                    val description = it["description"].asText("")
                    if (relevantText.isBlank() || description.isBlank()) null else RelevantText(
                        relevantText, description
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    override suspend fun extractRemoveTimers(friendshipId: FriendshipId, message: UserMessage): List<RelevantText> {
        return extractTimers("remove", friendshipId, message)
    }

    override suspend fun extractListTimers(friendshipId: FriendshipId, message: UserMessage): List<RelevantText> {
        return extractTimers("list", friendshipId, message)
    }

    private suspend fun extractTimers(
        action: String, friendshipId: FriendshipId, message: UserMessage,
    ): List<RelevantText> {
        val promptText = when (action) {
            "set" -> """
You are a helpful assistant. Your task is to extract all timer-related segments from the following message related to setting a timer.

A timer consists of:
- a duration (e.g., 30 minutes, 1h 15m, etc.)
- an optional label/description (e.g., "for pizza", "to water the plants")

For each timer mentioned, return:
- relevantText: the complete relevant portion of the message the user wrote
- description: short description of the task from the user's point of view

❗ Only include the timer label/description if the user clearly provides one or it is closely tied to the duration (e.g., "timer for the cake").
If it's necessary, you can split the message and recombine parts without altering its contents.

Output must be a JSON array like this:
[
  {
    "relevantText": "...",
    "description": "..."
  }
]

Message:
"${message.text}"
            """.trimIndent()

            "remove" -> """
You are a helpful assistant. Your task is to extract all timer-related segments from the following message related to removing a timer.

A timer to remove can be identified by:
- Its description or label (e.g., "the pizza timer", "Tea timer")
- Explicit or implicit removal commands (e.g., "remove", "delete", "cancel", "stop", or phrases like "Buy cola ... remove it")

For each timer removal mentioned, return:
- relevantText: the complete portion of the message that refers to removing the timer
- description: short description of the task from the user's point of view

Make sure to capture linguistic signals like "Add this task" and combinations such as "Buy cola ... remove it" correctly.

If it's necessary, you can split the message and recombine parts without altering its contents.

Output must be a JSON array like this:
[
  {
    "relevantText": "...",
    "description": "..."
  }
]

Message:
"${message.text}"
            """.trimIndent()

            "list" -> """
You are a helpful assistant. Your task is to extract all timer-related segments from the following message related to listing timers.

For each timer mentioned, return:
- relevantText: the complete relevant portion of the message the user wrote
- description: short description of the task from the user's point of view

Output must be a JSON array like this:
[
  {
    "relevantText": "...",
    "description": "..."
  }
]

Message:
"${message.text}"
            """.trimIndent()

            else -> """
You are a helpful assistant. Your task is to extract all timer-related segments from the following message related to the action "$action".

For each timer mentioned, return:
- relevantText: the complete relevant portion of the message the user wrote
- description: short description of the task from the user's point of view

Output must be a JSON array like this:
[
  {
    "relevantText": "...",
    "description": "..."
  }
]

Message:
"${message.text}"
            """.trimIndent()
        }

        val prompt = AiUserMessage(promptText)

        val zone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneId.of("UTC")
        val result = llmClient.promptReceivingJson(listOf(prompt), options, zone, message.receivedAt)

        return result?.let { json ->
            try {
                val parsed = objectMapper.readTree(json)
                parsed.mapNotNull {
                    val relevantText = it["relevantText"].asText("")
                    val description = it["description"].asText("")
                    if (relevantText.isBlank() || description.isBlank()) null else RelevantText(
                        relevantText, description
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }
}