package icu.neurospicy.fibi.outgoing.ollama

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.module.kotlin.readValue
import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.FibiMessage
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Message
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.model.events.IntentRecognitionFinished
import icu.neurospicy.fibi.domain.model.events.IntentRecognitionStarted
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import org.springframework.ai.chat.messages.UserMessage as AiUserMessage

private const val BASE_MODEL = "qwen2.5:14b"

@Service
class IntentRecognizer(
    private val llmClient: LlmClient,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val friendshipLedger: FriendshipLedger,
    private val objectMapper: ObjectMapper,
    private val intentRecognitionRepository: IntentRecognitionRepository,
    private val promptsConfiguration: PromptsConfiguration,
) {
    suspend fun recognize(
        friendshipId: FriendshipId,
        message: UserMessage,
        possibleIntents: Set<PossibleIntent>,
        history: List<Message>? = null,
        useTools: Boolean
    ): Result {
        applicationEventPublisher.publishEvent(
            IntentRecognitionStarted(
                _source = this.javaClass, friendshipId, message.channel, message.messageId
            )
        )
        val intentRecognitionPrompt = createIntentRecognitionPrompt(message, possibleIntents, history)
        val llmResponse = queryLlmFor(intentRecognitionPrompt, friendshipId, message.receivedAt)
        val recognitionResult = extractRecognitionResultFrom(llmResponse, message, possibleIntents)
        applicationEventPublisher.publishEvent(
            IntentRecognitionFinished(
                _source = this.javaClass,
                friendshipId,
                message.channel,
                message.messageId,
                recognitionResult.intent,
                intentEmoji = recognitionResult.intent.emoji
            )
        )
        return recognitionResult
    }

    private fun extractRecognitionResultFrom(
        sanitizedMessage: String, message: UserMessage, possibleIntents: Set<PossibleIntent>
    ): Result {
        val llmResult = try {
            val llmResult = objectMapper.readValue<LlmResult>(sanitizedMessage)
            intentRecognitionRepository.recognized(
                message.messageId, message.text, llmResult.intent, llmResult.likelyOtherIntents, BASE_MODEL
            )
            Result(
                possibleIntents.find { it.name == llmResult.intent }!!,
                llmResult.likelyOtherIntents.distinct().mapNotNull { name -> possibleIntents.find { it.name == name } }
                    .toSet())
        } catch (e: InvalidDefinitionException) {
            LOG.error("Failed to handle llm response '{}'", sanitizedMessage, e)
            throw IntentRecognitionFailed(
                "Failed to extract intent from llm response '$sanitizedMessage'",
                e
            )
        }
        return llmResult
    }

    private suspend fun queryLlmFor(
        intentRecognitionPrompt: List<org.springframework.ai.chat.messages.Message>,
        friendshipId: FriendshipId,
        receivedAt: Instant
    ): String {
        val llmResponse = try {
            val answer = llmClient.promptReceivingJson(
                intentRecognitionPrompt,
                OllamaOptions.builder().model(BASE_MODEL).temperature(0.4).build(),
                friendshipLedger.findBy(friendshipId)?.timeZone ?: UTC,
                receivedAt
            )
            LOG.debug("Llm result (raw): $answer")
            answer
        } catch (e: Exception) {
            LOG.error("Failed to get response by ollama.", e)
            throw IntentRecognitionFailed("Failed to get response by ollama.", e)
        }
        return llmResponse ?: throw IntentRecognitionFailed("No response by ollama for intent recognition")
    }

    private fun createIntentRecognitionPrompt(
        message: UserMessage, possibleIntents: Set<PossibleIntent>, history: List<Message>?
    ): List<org.springframework.ai.chat.messages.Message> = listOf(
        SystemMessage(promptsConfiguration.intentRecognitionSystemPromptTemplate),
        AiUserMessage(
            promptsConfiguration.intentRecognitionPromptTemplate.replace(
                "\${possibleIntents}",
                possibleIntents.joinToString("\n") { "\"${it.name}\": ${it.description}" })
                .replace("\${chatHistory}", history?.let { chatHistory(it) } ?: "")
                .replace("\${messageText}", message.text)))


    private fun chatHistory(history: List<Message>) = "\n\nChat history:\n${
        history.joinToString("\n") {
            when (it) {
                is FibiMessage -> "${it.sentAt.atZone(UTC).format(ISO_DATE_TIME)} fibi: \"${it.text}\""
                is UserMessage -> "${it.receivedAt.atZone(UTC).format(ISO_DATE_TIME)} user: \"${it.text}\""
            }
        }
    }\n"

    companion object {
        private val LOG = LoggerFactory.getLogger(IntentRecognizer::class.java)
    }
}

class IntentRecognitionFailed(message: String, exception: Exception? = null) : Throwable(message, exception)

data class LlmResult(
    val intent: String, val likelyOtherIntents: List<String> = emptyList()
)

data class Result(
    val intent: PossibleIntent, val likelyOtherIntents: Set<PossibleIntent> = emptySet()
)

data class PossibleIntent(
    val name: String,
    val description: String,
    val emoji: String = "💬",//Emoji representing the intent. Signal messages are marked with this emoji
)