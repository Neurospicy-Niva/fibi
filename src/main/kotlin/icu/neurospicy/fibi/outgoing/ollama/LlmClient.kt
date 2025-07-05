package icu.neurospicy.fibi.outgoing.ollama;

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min


@Service
class LlmClient(
    private val chatClient: ChatClient,
) {
    suspend fun promptReceivingJson(
        messages: List<Message>,
        ollamaOptions: OllamaOptions,
        timezone: ZoneId,
        receivedAt: Instant,
        context: Map<String, Any>? = null,
        tools: Set<Any>? = null,
        retryConfig: RetryConfig = RetryConfig(),
    ): String? {
        var options = ollamaOptions
        repeat(retryConfig.maxRetries + 1) { trial ->
            try {
                val answer =
                    chatClient.prompt(Prompt(messages, options))
                        .apply { tools?.takeIf { it.isNotEmpty() }?.let { this.tools(it) } }
                        .toolContext(
                            (context?.plus(loadDefaultContext(timezone, receivedAt)) ?: loadDefaultContext(
                                timezone, receivedAt
                            ))
                        ).let { prompt -> prompt.call().content()?.let { sanitize(it) } }
                if (!answer.isNullOrBlank()) return answer
            } catch (e: Exception) {
                if (retryConfig.failWithException && trial == retryConfig.maxRetries) throw e
            }
            options = retryConfig.adaptOptionsOnRetry(options)
        }
        return null
    }

    suspend fun promptReceivingText(
        messages: List<Message>,
        ollamaOptions: OllamaOptions,
        timezone: ZoneId,
        receivedAt: Instant,
        context: Map<String, Any>? = null,
        tools: Set<Any>? = null,
        retryConfig: RetryConfig = RetryConfig(),
    ): String? {
        var options = ollamaOptions
        repeat(retryConfig.maxRetries + 1) { trial ->
            try {
                val prompt = chatClient.prompt(Prompt(messages, options))
                tools?.takeIf { it.isNotEmpty() }?.let { prompt.tools(it) }
                val answer = prompt.toolContext(
                    (context?.plus(loadDefaultContext(timezone, receivedAt)) ?: loadDefaultContext(
                        timezone, receivedAt
                    ))
                ).call().content()
                if (!answer.isNullOrBlank()) return removeThinking(answer)
            } catch (e: Exception) {
                if (retryConfig.failWithException && trial == retryConfig.maxRetries) throw e
            }
            options = retryConfig.adaptOptionsOnRetry(options)
        }
        return null
    }

    fun loadDefaultContext(timezone: ZoneId, receivedAt: Instant): Map<String, Any> {
        return mapOf(
            "timezone" to timezone, "receivedAt" to receivedAt,
        )
    }

    private fun removeThinking(detailsJson: String): String {
        return detailsJson.replace(Regex("<think>.*</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
    }

    private fun sanitize(detailsJson: String): String {
        return detailsJson.trimIndent().replace(Regex("</?tool_call>"), "")
            .replace(Regex("<think>.*</think>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("(?i)\"name\"\\s*:\\s*\"set[_a-z]*\""), "").replace(
                Regex("^:*```(json)?\\n", RegexOption.DOT_MATCHES_ALL), ""
            ).replace(Regex("\\n```.*$", RegexOption.DOT_MATCHES_ALL), "").let { text ->
                val start = min(text.indexOf('{'), text.indexOf('['))
                val end = max(text.lastIndexOf('}'), text.lastIndexOf(']'))
                if (start != -1 && end != -1 && start < end) text.substring(start, end + 1) else text
            }
    }
}

data class RetryConfig(
    val maxRetries: Int = 3,
    val failWithException: Boolean = false,
    val adaptOptionsOnRetry: (options: OllamaOptions) -> OllamaOptions = {
        it.temperature = it.temperature?.plus(0.1) ?: 0.3; it
    },
)