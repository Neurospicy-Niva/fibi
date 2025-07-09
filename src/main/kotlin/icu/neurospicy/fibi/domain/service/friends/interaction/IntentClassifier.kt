package icu.neurospicy.fibi.domain.service.friends.interaction

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.Conversation
import icu.neurospicy.fibi.domain.model.Message
import icu.neurospicy.fibi.domain.model.events.IncomingFriendMessageReceived
import icu.neurospicy.fibi.domain.service.friends.interaction.tasks.TaskIntents
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset

@Service
class IntentClassifier(
    private val llmClient: LlmClient, 
    private val intentRegistry: IntentRegistry, 
    private val objectMapper: ObjectMapper,
    private val defaultModel: String,
    private val complexTaskModel: String
) {
    data class IntentClassification(val intent: Intent, val confidence: Float)

    suspend fun classifyIntent(conversation: Conversation): List<IntentClassification> {
        val allIntents = intentRegistry.getDescriptions()
        return classifyIntent(
            message = conversation.messages.last(), prompt = """
Based on the final user message, identify the user's actual *intention or goal* (not just keywords or content). Classify into one of the following intents:
${allIntents.entries.joinToString("\n") { "- \"${it.key.name}\": ${it.value}" }}

⚠️ Completely ignore intents at the beginning of the conversation. Focus on the last message.

Return a JSON array of objects with 'intent' and 'confidence' fields.
Example:
[
  { "intent": "<intent>", "confidence": 0.8 },
  { "intent": "<intent>", "confidence": 0.75 },
  { "intent": "<intent>", "confidence": 0.05 }
]

Conversation:
${conversation.messages.joinToString("\n") { "${if (it.byUser()) "User: " else "System: "}\"${it.text}\"\n---" }}
        """.trimIndent()
        )

    }

    suspend fun classifyIntent(event: IncomingFriendMessageReceived): List<IntentClassification> {
        val allIntents = intentRegistry.getDescriptions()
        return classifyIntent(
            message = event.message, prompt = """
Classify the user's message into one of the following intents:
${allIntents.entries.joinToString("\n") { "- \"${it.key.name}\": ${it.value}" }}

User message:
 "${event.message.text}"
---

Ignore intents at the beginning of the conversation. Focus on the end.

Return a JSON array of objects with 'intent' and 'confidence' fields.
Example:
[
  { "intent": "<intent>", "confidence": 0.8 },
  { "intent": "<intent>", "confidence": 0.75 },
  { "intent": "<intent>", "confidence": 0.05 }
]
        """.trimIndent()
        )
    }

    private suspend fun classifyIntent(message: Message, prompt: String): List<IntentClassification> {
        return coroutineScope {
            val llmResultAsync = deferredClassifyIntents(prompt)
            val addTaskIsHighlyIntended = verifyIfAddingTaskIsHighlyIntended(message)
            val llmClassification = llmResultAsync.await()
            return@coroutineScope if (!addTaskIsHighlyIntended) {
                llmClassification
            } else {
                llmClassification.filter { it.intent != TaskIntents.Add } + IntentClassification(TaskIntents.Add, 1f)
            }
        }
    }

    private fun CoroutineScope.deferredClassifyIntents(prompt: String): Deferred<List<IntentClassification>> = async {
        try {
            val response = llmClient.promptReceivingJson(
                listOf(
                    UserMessage(prompt)
                ),
                OllamaOptions.builder().model(defaultModel).temperature(0.0).topP(0.3).build(),
                ZoneOffset.UTC,
                Instant.now()
            ) ?: "[]"
            parseIntentClassification(response, intentRegistry.getAll())

        } catch (e: Exception) {
            // Fallback to Unknown intent if parsing fails
            listOf(IntentClassification(intent = Intent("Unknown"), confidence = 0f))
        }
    }

    private suspend fun verifyIfAddingTaskIsHighlyIntended(message: Message): Boolean = llmClient.promptReceivingText(
        listOf(
            UserMessage(
                """Does the user **clearly and explicitly** want to add a task to their task list?
    
    Return only:
    - yes → if the user gives a clear instruction to create a task (e.g. "Add a task to call the clinic")
    - no → in all other cases, including vague, indirect, or reminder-like expressions
    
    The user's message:
    "${message.text}"
    
    Answer only: yes or no"""
            )
        ), OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.7).build(), ZoneOffset.UTC, Instant.now()
    )?.lowercase()?.startsWith("yes") == true

    private fun parseIntentClassification(
        response: String, availableIntents: List<Intent>
    ): List<IntentClassification> {
        // Map of intent names to Intent objects for quick lookup
        val intentMap = availableIntents.associateBy { it.name }

        // Parse the JSON response into a list of IntentClassification objects
        @Suppress("UNCHECKED_CAST") val jsonArray = objectMapper.readValue(
            response.let { response }, List::class.java
        ) as List<Map<String, Any>>
        return jsonArray.mapNotNull { item ->
            val intentName = item["intent"] as? String ?: return@mapNotNull null
            val confidence = (item["confidence"] as? Number)?.toFloat() ?: 0f

            // Look up the Intent object by name, or fallback to Unknown
            val intent = intentMap[intentName] ?: Intent("Unknown")
            IntentClassification(intent, confidence)
        }.sortedByDescending { it.confidence }
    }
}