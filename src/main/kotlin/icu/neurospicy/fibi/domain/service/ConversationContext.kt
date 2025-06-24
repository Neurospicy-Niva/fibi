package icu.neurospicy.fibi.domain.service

import icu.neurospicy.fibi.domain.model.Conversation
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Message
import icu.neurospicy.fibi.domain.repository.ConversationRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import org.springframework.stereotype.Service
import java.time.Instant.now

@Service
class ConversationContextService(
    private val conversationRepository: ConversationRepository,
) {
    fun startNewConversation(
        friendshipId: FriendshipId,
        intent: Intent,
        message: Message? = null
    ): Conversation {
        return Conversation(intent, message?.let { listOf(message) } ?: emptyList(), now())
            .apply { conversationRepository.save(friendshipId, this) }
    }

    fun endConversation(friendshipId: FriendshipId) {
        conversationRepository.endConversation(friendshipId)
    }
}

