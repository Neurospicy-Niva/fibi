package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.repository.ConversationRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ConversationRepositoryUsingMongoDb(
    private val mongoTemplate: MongoTemplate,
) : ConversationRepository {
    override fun findByFriendshipId(friendshipId: FriendshipId): Conversation? {
        return mongoTemplate.findOne(
            query(where("friendshipId").`is`(friendshipId.toString())),
            ConversationOfFriend::class.java
        )
            ?.toConversation()
    }

    override fun save(friendshipId: FriendshipId, conversation: Conversation) {
        mongoTemplate.remove(
            query(where("friendshipId").`is`(friendshipId.toString())),
            ConversationOfFriend::class.java
        )
        mongoTemplate.save(ConversationOfFriend.fromConversation(friendshipId, conversation))
    }

    override fun addFibisResponse(friendshipId: FriendshipId, message: FibiMessage) {
        mongoTemplate.save(
            mongoTemplate.findOne(
                query(where("friendshipId").`is`(friendshipId.toString())),
                ConversationOfFriend::class.java
            )?.let {
                it.copy(messages = it.messages + message)
            } ?: return
        )
    }

    override fun addUserResponse(
        friendshipId: FriendshipId,
        message: UserMessage
    ) {
        mongoTemplate.save(
            mongoTemplate.findOne(
                query(where("friendshipId").`is`(friendshipId.toString())),
                ConversationOfFriend::class.java
            )?.let {
                it.copy(messages = it.messages + message)
            } ?: return
        )
    }

    override fun endConversation(friendshipId: FriendshipId) {
        mongoTemplate.remove(
            query(where("friendshipId").`is`(friendshipId.toString())),
            ConversationOfFriend::class.java
        )
    }
}

@Document(collection = "conversations")
data class ConversationOfFriend(
    @Id
    val friendshipId: FriendshipId,
    val intent: Intent,
    val messages: List<Message>,
    val lastInteraction: Instant,
) {
    fun toConversation(): Conversation = Conversation(intent, messages, lastInteraction)

    companion object {
        fun fromConversation(friendshipId: FriendshipId, conversation: Conversation): ConversationOfFriend =
            ConversationOfFriend(
                friendshipId,
                conversation.intent,
                conversation.messages,
                conversation.lastInteraction
            )
    }
}
