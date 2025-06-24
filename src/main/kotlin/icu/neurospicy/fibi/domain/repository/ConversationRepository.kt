package icu.neurospicy.fibi.domain.repository

import icu.neurospicy.fibi.domain.model.Conversation
import icu.neurospicy.fibi.domain.model.FibiMessage
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage

interface ConversationRepository {
    fun findByFriendshipId(friendshipId: FriendshipId): Conversation?
    fun save(friendshipId: FriendshipId, conversation: Conversation)
    fun addFibisResponse(friendshipId: FriendshipId, message: FibiMessage)
    fun addUserResponse(friendshipId: FriendshipId, message: UserMessage)
    fun endConversation(friendshipId: FriendshipId)
}
