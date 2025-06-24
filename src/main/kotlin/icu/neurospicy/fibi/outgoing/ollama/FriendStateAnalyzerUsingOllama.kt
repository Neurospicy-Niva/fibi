package icu.neurospicy.fibi.outgoing.ollama

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FibiMessage
import icu.neurospicy.fibi.domain.model.Message
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.communication.FriendStateAnalysisResult
import icu.neurospicy.fibi.domain.service.friends.communication.FriendStateAnalyzer
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset

@Component
class FriendStateAnalyzerUsingOllama(
    private val llmClient: LlmClient,
    private val objectMapper: ObjectMapper,
) : FriendStateAnalyzer {
    override suspend fun analyze(
        messages: List<Message>,
        questions: List<String>,
    ): FriendStateAnalysisResult {
        val prompt =
            "## Questions\n" + questions.joinToString("\n") { "- $it" } +
                    "\n" +
                    "## Conversation\n" + messages.joinToString("\n") { message ->
                when (message) {
                    is FibiMessage -> "Assistant:\n\"$message\""
                    is UserMessage -> "User:\n\"$message\""
                }
            }

        val json = llmClient.promptReceivingJson(
            listOf(SystemMessage(systemPrompt), org.springframework.ai.chat.messages.UserMessage(prompt)),
            OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.8).build(),
            ZoneOffset.UTC, Instant.now()
        )
        return objectMapper.readValue(json, FriendStateAnalysisResult::class.java)
    }

    private val systemPrompt = """
You are an empathetic assistant with a deep understanding of human emotions, particularly in the context of neurodivergent individuals such as those with ADHD and Autism.

You will receive:
- A conversation between a user and an assistant
- A list of yes/no questions to answer based on the user’s intent and mood

Your goals are to:
1. Estimate the user's mood based on their messages.
2. Answer the yes/no questions based **only on the user’s messages**, not the assistant’s.

Please return your results in the following JSON format:
{
  "emotions": [
    {
      "mood": "<Neutral|Calm|Stressed|Angry|Sad|Energized>",
      "confidence": <float from 0.0 to 1.0>
    }
  ],
  "answers": [
    {
      "question": "<exact question>",
      "answer": <true | false | null>
    }
  ]
}

Definitions:
- "Energized": motivated, upbeat, confident, or forward-moving in tone.
- "Calm": thoughtful, emotionally balanced, steady, or grounded.
- "Stressed": overwhelmed, tense, pressured, or worried.
- "Angry": irritated, aggressive, dismissive, or harshly rejecting.
- "Sad": disappointed, hopeless, downcast, or self-deprecating.
- "Neutral": disinterested, detached, indifferent, or without emotional expression.

Notes:
- Base your judgment only on the user’s wording and tone.
- The results will influence how an assistant continues a sensitive conversation, especially when the user may be emotionally overwhelmed.
- If unsure about an answer, respond with “null”.
""".trimIndent()
}