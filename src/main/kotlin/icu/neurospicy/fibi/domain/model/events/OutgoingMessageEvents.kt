package icu.neurospicy.fibi.domain.model.events

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.domain.model.OutgoingMessage
import org.springframework.context.ApplicationEvent

data class SendMessageCmd(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val outgoingMessage: OutgoingMessage,
    /**
     * If the outgoing message is a response to a previous message, this refers to the request.
     */
    val answerToMessageId: MessageId? = null
) : ApplicationEvent(_source)