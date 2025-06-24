package icu.neurospicy.fibi.domain.service

import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import java.time.Instant.now

open class MessageTest {
    protected fun incomingSignalMessageWith(content: String?, timestamp: Long = now().epochSecond) = UserMessage(
        messageId = SignalMessageId(timestamp),
        text = content ?: "Some message",
        channel = Channel.SIGNAL,
        receivedAt = now(),
    )
}