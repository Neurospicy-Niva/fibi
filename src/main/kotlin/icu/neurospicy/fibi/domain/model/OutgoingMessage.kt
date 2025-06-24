package icu.neurospicy.fibi.domain.model

import org.springframework.ai.chat.messages.AssistantMessage

sealed interface OutgoingMessage {
    val messageId: FibiMessageId
    val channel: Channel
    val toolCalls: List<AssistantMessage.ToolCall>
}

data class OutgoingTextMessage(
    override val channel: Channel,
    val text: String,
    override val toolCalls: List<AssistantMessage.ToolCall> = emptyList(),
    override val messageId: FibiMessageId = FibiMessageId()
) : OutgoingMessage

sealed interface OutgoingMessageNeedsGenerator : OutgoingMessage {
    val useHistory: Boolean
    val useTaskActions: Boolean
    val useRetrievalTools: Boolean
    val useFriendSettingActions: Boolean
}

data class OutgoingGeneratedMessage(
    override val channel: Channel,
    val messageDescription: String,
    override val useHistory: Boolean = true,
    override val useTaskActions: Boolean = true,
    override val useRetrievalTools: Boolean = true,
    override val useFriendSettingActions: Boolean = true,
    override val toolCalls: List<AssistantMessage.ToolCall> = emptyList(),
    override val messageId: FibiMessageId = FibiMessageId()
) : OutgoingMessageNeedsGenerator

data class OutgoingAdaptedTextMessage(
    override val channel: Channel,
    val messageDescription: String,
    val text: String,
    override val useHistory: Boolean = true,
    override val useTaskActions: Boolean = true,
    override val useRetrievalTools: Boolean = true,
    override val useFriendSettingActions: Boolean = true,
    override val toolCalls: List<AssistantMessage.ToolCall> = emptyList(),
    override val messageId: FibiMessageId = FibiMessageId()
) : OutgoingMessageNeedsGenerator