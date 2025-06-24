package icu.neurospicy.fibi.incoming.signal

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.SignalId
import org.slf4j.LoggerFactory
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service

@Service
class SignalEventHandler(
    private val objectMapper: ObjectMapper,
    private val signalMessageHandler: SignalMessageHandler,
    private val signalReactionHandler: SignalReactionHandler
) {
    fun process(event: ServerSentEvent<*>?) {
        val rawData = event?.data() ?: return
        val rawDataString = when (rawData) {
            is String -> rawData
            is Map<*, *> -> objectMapper.writeValueAsString(rawData)
            else -> {
                LOG.warn("SSE data was not a map but {}? data={}", rawData.javaClass, rawData)
                return
            }
        }
        try {
            val rpcEvent = objectMapper.readValue(rawDataString, SignalRpcEvent::class.java)
            val envelope = rpcEvent.envelope ?: return

            // Process reactions
            envelope.reaction?.let { reaction ->
                signalReactionHandler.process(envelope.source, reaction)
                return
            }

            // Process messages
            envelope.dataMessage?.let { dataMessage ->
                signalMessageHandler.process(envelope, dataMessage.message, rawData.toString())
                return
            }

            LOG.debug("Unhandled event type in envelope: {}", envelope)
        } catch (e: Exception) {
            LOG.error("Failed to parse SSE event '{}' as SignalRpcEvent. error={}", event, e.message, e)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SignalMessageHandler::class.java)
    }
}

/**
 * Minimal JSON structure matching the SSE event from signal-cli.
 */
data class SignalRpcEvent(
    val jsonrpc: String?,
    val method: String?,
    val envelope: EnvelopeData? = null
)

data class EnvelopeData(
    val source: String? = null,
    val sourceNumber: String? = null,
    val sourceUuid: String? = null,
    val sourceName: String? = null,
    val sourceDevice: Int? = null,
    val timestamp: Long? = null,
    val dataMessage: DataMessage? = null,
    val reaction: Reaction? = null,
    val typingMessage: TypingMessage? = null
)

data class DataMessage(
    val message: String? = null,
    val expiresInSeconds: Int? = null,
    val viewOnce: Boolean? = null
)

data class Reaction(
    val emoji: String? = null,
    val targetAuthor: String? = null,
    val targetSentTimestamp: Long? = null,
    val isRemove: Boolean? = null
)

data class TypingMessage(
    val action: String? = null,
    val timestamp: Long? = null
)

data class ReactionEvent(
    val userId: SignalId,
    val emoji: String?,
    val targetAuthor: String?,
    val targetSentTimestamp: Long?,
    val isRemove: Boolean?
)