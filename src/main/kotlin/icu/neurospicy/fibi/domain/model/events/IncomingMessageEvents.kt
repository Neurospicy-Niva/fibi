package icu.neurospicy.fibi.domain.model.events

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.LedgerEntry
import icu.neurospicy.fibi.domain.model.UserMessage
import org.springframework.context.ApplicationEvent

data class UnidentifiedIncomingMessageReceived(
    val entry: LedgerEntry,
    val incomingMessage: UserMessage,
    val rawEvent: String
) : ApplicationEvent(incomingMessage.channel)

data class MessageForActivityReceived(
    val activity: String,
    val friendshipId: FriendshipId,
    val message: UserMessage
) : ApplicationEvent(message.channel)

data class IncomingFriendMessageReceived(
    val friendshipId: FriendshipId,
    val message: UserMessage
) : ApplicationEvent(message.channel)

data class IncomingAcquaintanceMessageReceived(
    val friendshipId: FriendshipId,
    val message: UserMessage
) : ApplicationEvent(message.channel)

data class IncomingCuriousMessageReceived(
    val friendshipId: FriendshipId,
    val message: UserMessage
) : ApplicationEvent(message.channel)