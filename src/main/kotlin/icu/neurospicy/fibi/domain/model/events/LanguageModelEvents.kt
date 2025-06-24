package icu.neurospicy.fibi.domain.model.events

import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import icu.neurospicy.fibi.outgoing.ollama.PossibleIntent
import org.springframework.context.ApplicationEvent

data class MessageGenerationStarted(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val channel: Channel,
    val messageId: MessageId?
) : ApplicationEvent(_source)

data class MessageGenerationFinished(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val channel: Channel,
    val messageId: MessageId?
) : ApplicationEvent(_source)

data class IntentRecognitionStarted(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val channel: Channel,
    val messageId: MessageId
) : ApplicationEvent(_source)

data class IntentRecognitionFinished(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val channel: Channel,
    val messageId: MessageId,
    val possibleIntent: PossibleIntent?,
    val intentEmoji: String? = null
) : ApplicationEvent(_source)


data class MessageInteractionStarted(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val channel: Channel,
    val messageId: MessageId
) : ApplicationEvent(_source)

data class MessageInteractionFinished(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val channel: Channel,
    val messageId: MessageId,
) : ApplicationEvent(_source)