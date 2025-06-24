package icu.neurospicy.fibi.outgoing.ollama

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.ChatRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.service.friends.tools.ChatHistoryTools
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.ollama.api.OllamaModel
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Service
import java.time.Instant.now
import java.time.ZoneOffset.UTC

@Service
class InformationExtractor(
    private val objectMapper: ObjectMapper,
    private val llmClient: LlmClient,
    private val friendshipLedger: FriendshipLedger,
    private val chatRepository: ChatRepository,
    private val promptsConfiguration: PromptsConfiguration,
) {

    suspend fun <T> extract(
        text: String,
        spec: ExtractionSpec<T>,
        friendshipId: FriendshipId? = null,
        additionalTools: List<Any> = emptyList()
    ): T {
        // Generate a prompt based on the target type and text.
        val userMessage = UserMessage(generatePrompt(text, spec))
        val systemMessage = SystemMessage(promptsConfiguration.informationExtractionSystemPromptTemplate)

        val sanitizedMessage = llmClient.promptReceivingJson(
            listOf(systemMessage, userMessage),
            OllamaOptions.builder().model(OllamaModel.QWEN_2_5_7B).temperature(0.4).build(),
            friendshipId?.let { friendshipLedger.findBy(it)?.timeZone } ?: UTC,
            now(),
            tools = (friendshipId?.let {
                additionalTools.plus(ChatHistoryTools(friendshipLedger, chatRepository, friendshipId))
            } ?: additionalTools).toSet()) ?: ""
        LOG.debug("Information extraction succeeded. Mapping response to expected spec.")
        try {
            return objectMapper.readValue(sanitizedMessage, spec.targetType)
        } catch (ex: Exception) {
            LOG.debug("Information could not be extracted: {}", sanitizedMessage)
            throw ExtractionException("Failed to extract information", ex)
        }
    }


    private fun <T> generatePrompt(text: String, spec: ExtractionSpec<T>): String {
        val fields =
            spec.expectedFields?.joinToString() { field -> "\"${field.name}\": ${field.type} (${if (field.required) "required" else "optional"})" }
                ?: spec.targetType.declaredFields.joinToString(separator = ",\n") { field ->
                    val required = if (!field.type.isNullable()) "required" else "optional"
                    "\"${field.name}\": ${field.type.simpleName} ($required)"
                }
        return promptsConfiguration.informationExtractionPromptTemplate.replace("\${fields}", fields)
            .replace("\${additionalContext}", spec.additionalContext ?: "").replace("\${text}", text)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}

class ExtractionException(msg: String, exception: Exception) : java.lang.Exception(msg, exception)

fun Class<*>.isNullable(): Boolean {
    // Simplified; in Kotlin you might use reflection or metadata inspection
    return name.endsWith("?")
}

class ExtractionSpec<T>(
    val targetType: Class<T>,
    val additionalContext: String? = null,
    val expectedFields: List<ExpectedField>? = null,
)

data class ExpectedField(val name: String, val type: String, val required: Boolean = true)