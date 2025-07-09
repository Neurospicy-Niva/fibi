package icu.neurospicy.fibi.domain.service.friends.interaction

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@Service
class ExtractParamFromConversationService(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
    private val complexTaskModel: String,
) {
    suspend fun extractLocalTime(question: String, answer: String): ExtractParamResult<LocalTime> {
        val prompt = buildPromptToExtractTypedValue(
            typeName = "LocalTime", expectedFormat = "HH:mm", example = "07:00", question = question, answer = answer
        )
        return llmClient.promptReceivingJson(
            listOf(org.springframework.ai.chat.messages.UserMessage(prompt)),
            OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.7).build(),
            ZoneOffset.UTC,
            Instant.now()
        )?.let { json ->
            val result = objectMapper.readTree(json)
            if (result["clarified"].asBoolean() && result["answer"] != null) {
                ExtractParamResult(value = LocalTime.parse(result["answer"].asText()))
            } else {
                ExtractParamResult(clarifyingQuestion = result["clarifyingQuestion"]?.asText())
            }
        } ?: ExtractParamResult(failed = true)
    }

    suspend fun extractInstant(question: String, answer: String): ExtractParamResult<LocalDateTime> {
        val prompt = buildPromptToExtractDateTime(question, answer)
        return llmClient.promptReceivingJson(
            listOf(org.springframework.ai.chat.messages.UserMessage(prompt)),
            OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.7).build(),
            ZoneOffset.UTC,
            Instant.now()
        )?.let { json ->
            val result = objectMapper.readTree(json)
            if (result["clarified"].asBoolean() && result["answer"] != null) {
                ExtractParamResult(value = LocalDateTime.parse(result["answer"].asText()))
            } else {
                ExtractParamResult(clarifyingQuestion = result["clarifyingQuestion"]?.asText())
            }
        } ?: ExtractParamResult(failed = true)
    }

    suspend fun extractLocalString(question: String, answer: String): ExtractParamResult<String> {
        val prompt = buildPromptToExtractTypedValue(
            typeName = "text", expectedFormat = null, example = null, question = question, answer = answer
        )
        return llmClient.promptReceivingJson(
            listOf(org.springframework.ai.chat.messages.UserMessage(prompt)),
            OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.7).build(),
            ZoneOffset.UTC,
            Instant.now()
        )?.let { json ->
            val result = objectMapper.readTree(json)
            if (result["clarified"].asBoolean() && result["answer"] != null) {
                ExtractParamResult(value = result["answer"].asText())
            } else {
                ExtractParamResult(clarifyingQuestion = result["clarifyingQuestion"]?.asText())
            }
        } ?: ExtractParamResult(failed = true)
    }

    suspend fun <T : Number> extractNumber(
        question: String,
        answer: String,
        returnType: Class<T>,
    ): ExtractParamResult<T> {
        val typeAsString = when (returnType) {
            Float::class.java -> "Float"
            Int::class.java -> "Integer"
            else -> throw IllegalArgumentException("Just supports Float and Int")
        }
        val prompt = buildPromptToExtractNumber(typeAsString, question, answer)
        return llmClient.promptReceivingJson(
            listOf(org.springframework.ai.chat.messages.UserMessage(prompt)),
            OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.7).build(),
            ZoneOffset.UTC,
            Instant.now()
        )?.let { json ->
            val result = objectMapper.readTree(json)

            @Suppress("UNCHECKED_CAST") val value = when (returnType) {
                Float::class.java -> result["answer"]?.asDouble()?.toFloat() as T?
                Int::class.java -> result["answer"]?.asInt() as T?
                else -> null
            }
            if (result["clarified"].asBoolean() && value != null) {
                ExtractParamResult(value = value)
            } else {
                ExtractParamResult(clarifyingQuestion = result["clarifyingQuestion"]?.asText())
            }
        } ?: ExtractParamResult(failed = true)
    }

    suspend fun extractBoolean(question: String, answer: String): ExtractParamResult<Boolean> {
        val prompt = buildPromptToExtractTypedValue(
            typeName = "Boolean", expectedFormat = null, example = "true", question = question, answer = answer
        )
        return llmClient.promptReceivingJson(
            listOf(org.springframework.ai.chat.messages.UserMessage(prompt)),
            OllamaOptions.builder().model(complexTaskModel).temperature(0.0).topP(0.7).build(),
            ZoneOffset.UTC,
            Instant.now()
        )?.let { json ->
            val result = objectMapper.readTree(json)
            if (result["clarified"].asBoolean() && result["answer"] != null) {
                ExtractParamResult(value = result["answer"].asBoolean())
            } else {
                ExtractParamResult(clarifyingQuestion = if (result["clarifyingQuestion"] != null) result["clarifyingQuestion"].asText() else null)
            }
        } ?: ExtractParamResult(failed = true)
    }

    private fun buildPromptToExtractDateTime(question: String, answer: String): String = """
You are a helpful assistant that extracts a date and time representing the answer to a question from user input.

Responsibilities:
1. Determine if the user answered the question OR if a clarifying question is needed.
2. If the user answered, determine the date and time being the answer to the question.
3. If the user did not answer the question, articulate a clarifying response reiterating the question as "clarifyingQuestion". 

⚠ DO NOT guess. Use only the date and time mentioned in the user answer.
⚠ Use the current date to determine relative times. Today is ${Instant.now().atZone(ZoneOffset.UTC)}.
          
Conversation:
Question
"$question"
---
User answer
"$answer"

Return a JSON object with keys:
- "clarified": true/false
- "answer": a valid ISO-8601 datetime string (e.g., "2025-06-01T08:00:00") answering the question
- "clarifyingQuestion": optional response if clarification is needed.
No chat, no explanation."""

    private fun buildPromptToExtractNumber(type: String, question: String, answer: String): String =
        """You are a helpful assistant that extracts a number of type $type from user input.

Responsibilities:
1. Determine if the user answered the question OR if a clarifying question is needed.
2. If the user answered, determine the number of type $type from the answer of the user.
3. If the user did not answer the question, articulate a clarifying response reiterating the question as "clarifyingQuestion". 

⚠ DO NOT guess. Use only the date and time mentioned in the user answer.
          
Conversation:
Question
"$question"
---
User answer
"$answer"

Return a JSON object with keys:
- "clarified": true/false
- "answer": the number, matching the expected type: $type answering the question.
- "clarifyingQuestion": optional response if clarification is needed.
No chat, no explanation."""

    // --- Prompt builder for typed value extraction ---
    private fun buildPromptToExtractTypedValue(
        typeName: String,
        expectedFormat: String?,
        example: String?,
        question: String,
        answer: String,
    ): String = ("""
You are a helpful assistant that extracts a value of type \"$typeName\" representing the answer to a question from a user answer.
          
Responsibilities:
1. Determine if the user answered the question OR if a clarifying question is needed.
2. If the user answered, determine the value of type $typeName being the answer to the question.
3. If the user did not answer the question, articulate a clarifying response reiterating the question as "clarifyingQuestion". 

⚠ DO NOT guess. Use only the information mentioned in the user answer.
          
Conversation:
Question
"$question"
---
User answer
"$answer"

Return a JSON object with keys:
- "clarified": true/false
- "answer": the value, matching the expected type $typeName${expectedFormat?.let { " in format \"$it\"" } ?: ""}${
        example?.let {
            ", e.g., \"$it\", "
        } ?: ""
    } answering the question.
- "clarifyingQuestion": optional response if clarification is needed.

No chat, no explanation.
            """.trimIndent())
}

data class ExtractParamResult<T>(
    val clarifyingQuestion: String? = null,
    val value: T? = null,
    val failed: Boolean = false,
) {
    val completed: Boolean get() = value != null
}