package icu.neurospicy.fibi.domain.model

import com.maximeroussy.invitrode.WordGenerator
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.stereotype.Indexed
import java.time.Instant
import java.time.Instant.now

sealed interface Message {
    val messageId: MessageId
    fun byUser(): Boolean
    fun toLlmMessage(): org.springframework.ai.chat.messages.Message

    val text: String
    val channel: Channel
}

data class FibiMessage(
    override val messageId: FibiMessageId,
    val sentAt: Instant,
    override val text: String,
    override val channel: Channel,
    val toolCalls: List<AssistantMessage.ToolCall>?
) : Message {
    override fun byUser() = false
    override fun toLlmMessage(): AssistantMessage =
        AssistantMessage(this.text, mapOf("sent at" to this.sentAt), this.toolCalls ?: emptyList())
}

data class UserMessage(
    override val messageId: MessageId,
    val receivedAt: Instant = now(),
    override val text: String,
    override val channel: Channel,
    val rawMessage: String? = null
) : Message {
    override fun byUser() = true
    override fun toLlmMessage(): org.springframework.ai.chat.messages.UserMessage =
        org.springframework.ai.chat.messages.UserMessage(this.text)
}

@Indexed
interface MessageId

@JvmInline
value class SignalMessageId(
    private val timestamp: Long
) : MessageId {
    fun toLong(): Long {
        return this.timestamp
    }
}

@JvmInline
value class FibiMessageId(
    private val word: String = WordGenerator().newWord(4).lowercase() + "_"
            + WordGenerator().newWord(4).lowercase() + "_"
            + WordGenerator().newWord(4).lowercase()
) : MessageId {
    override fun toString(): String = word
}