package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.RelevantText
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.ZoneId
import org.springframework.ai.chat.messages.UserMessage as AiUserMessage


interface TaskClassifier {
    suspend fun extractAddTasks(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractUpdateTasks(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractRemoveTasks(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractCompleteTasks(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
    suspend fun extractListTasks(friendshipId: FriendshipId, message: UserMessage): List<RelevantText>
}

@Component
class LlmTaskClassifier(
    private val llmClient: LlmClient,
    private val friendshipLedger: FriendshipLedger,
    private val objectMapper: ObjectMapper
) : TaskClassifier {

    private val options = OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.1).build()

    override suspend fun extractAddTasks(friendshipId: FriendshipId, message: UserMessage): List<RelevantText> {
        return extractTasks("add", friendshipId, message)
    }

    override suspend fun extractUpdateTasks(friendshipId: FriendshipId, message: UserMessage): List<RelevantText> {
        return extractTasks("update", friendshipId, message)
    }

    override suspend fun extractRemoveTasks(friendshipId: FriendshipId, message: UserMessage): List<RelevantText> {
        return extractTasks("remove", friendshipId, message)
    }

    override suspend fun extractCompleteTasks(friendshipId: FriendshipId, message: UserMessage): List<RelevantText> {
        return extractTasks("complete", friendshipId, message)
    }

    override suspend fun extractListTasks(friendshipId: FriendshipId, message: UserMessage): List<RelevantText> {
        return extractTasks("list", friendshipId, message)
    }

    private suspend fun extractTasks(
        action: String,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<RelevantText> {
        val promptText = when (action) {
            "add" -> """
You are a helpful assistant. Extract all task-related segments from the message where the user clearly intends to add a task.

This includes:
- task descriptions phrased as needs (e.g., "I need to call the clinic")
- confirmation phrases (e.g., "Add this task", "Make a note", "Add to my list")

Extract complete task mentions — if a task is introduced in one sentence and confirmed in another (e.g. "I need to call the clinic. Add this task."), combine them.

For each task to add, return:
- relevantText: the full portion of the message (even if split across sentences)
- description: summary of what the user wants to do
- title: if identifiable
- status: "incomplete" unless explicitly stated

Output a JSON array like:
[
  {
    "action": "add",
    "relevantText": "...",
    "description": "...",
    "title": "...",
    "status": "incomplete"
  }
]

Message:
"${message.text}"
            """.trimIndent()

            "remove" -> """
You are a helpful assistant. Extract all segments where the user clearly intends to remove a task.

This includes:
- direct references to deletion ("remove this", "delete the task", "take it off")
- indirect references ("I accidentally added X", "that shouldn't be in my list")

If the task description is in one part (e.g. "I accidentally added Buy cola") and the command in another ("Please remove it from the list"), combine both into the `relevantText`.

For each removal, return:
- relevantText: the full portion of the message that reflects both task and removal
- description: what task is being removed
- title: if it exists
- status: (optional)

Output a JSON array like:
[
  {
    "action": "remove",
    "relevantText": "...",
    "description": "...",
    "title": "...",
    "status": "incomplete"
  }
]

Message:
"${message.text}"
            """.trimIndent()

            else -> """
                You are a helpful assistant. Your task is to extract all task-related segments from the following message where the user intends to $action tasks.
                The message may contain multiple tasks and actions.

                Each extracted task should include:
                - a **title** (e.g., "buy milk")
                - an optional **description**
                - a **status** if relevant

                For each relevant portion, extract:
                - action: "$action"
                - relevantText: the exact portion of the user message related to the task and action
                - description: a concise summary of the task from the user’s perspective
                - title (optional)
                - status (optional)

                ❗ You may split and recombine text fragments if needed — but **do not change the meaning**.

                Output a JSON array like:
                [
                  {
                    "action": "$action",
                    "relevantText": "...",
                    "description": "...",
                    "title": "...",
                    "status": "incomplete"
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
                    val relevantText = it["relevantText"]?.asText("") ?: return@mapNotNull null
                    val description = it["description"]?.asText("") ?: return@mapNotNull null
                    if (relevantText.isBlank() || description.isBlank()) return@mapNotNull null
                    RelevantText(relevantText = relevantText, description = description)
                }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }
}