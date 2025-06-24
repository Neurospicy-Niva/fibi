package icu.neurospicy.fibi.domain.repository

import icu.neurospicy.fibi.domain.model.*
import java.time.Instant

interface ChatRepository {
    fun findHistory(friendshipId: FriendshipId): ChatHistory
    fun add(friendshipId: FriendshipId, incomingMessage: UserMessage, rawMessage: String? = null)
    fun add(friendshipId: FriendshipId, outgoingMessage: OutgoingMessage, text: String, sentAt: Instant)
    fun applyDeletionRequest(friendshipId: FriendshipId)
    fun find(friendshipId: FriendshipId, messageId: MessageId?): Message?
}
