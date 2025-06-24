package icu.neurospicy.fibi.domain.service.friends

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall

class InteractionResult(val text: String, val toolCalls: List<ToolCall> = emptyList())