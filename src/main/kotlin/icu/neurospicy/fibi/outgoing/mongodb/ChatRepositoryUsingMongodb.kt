package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.repository.ChatRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
class ChatRepositoryUsingMongodb(
    private val mongoTemplate: MongoTemplate
) : ChatRepository {
    override fun findHistory(friendshipId: FriendshipId): ChatHistory {
        return mongoTemplate.findOne(
            query(where("friendshipId").`is`(friendshipId.toString())), ChatHistory::class.java, "chat"
        ) ?: ChatHistory(null, friendshipId, Stack())
    }

    override fun find(friendshipId: FriendshipId, messageId: MessageId?): Message? {
        return findHistory(friendshipId).timeline.find { it.messageId == messageId }
    }

    override fun add(friendshipId: FriendshipId, incomingMessage: UserMessage, rawMessage: String?) {
        val history = findHistory(friendshipId)
        history.timeline.add(
            UserMessage(
                incomingMessage.messageId,
                incomingMessage.receivedAt,
                incomingMessage.text,
                incomingMessage.channel,
                rawMessage
            )
        )
        mongoTemplate.save(history, "chat")
    }

    override fun add(friendshipId: FriendshipId, outgoingMessage: OutgoingMessage, text: String, sentAt: Instant) {
        val history = findHistory(friendshipId)
        history.timeline.add(
            FibiMessage(
                outgoingMessage.messageId,
                sentAt,
                text,
                outgoingMessage.channel,
                outgoingMessage.toolCalls
            )
        )
        mongoTemplate.save(history, "chat")
    }

    override fun applyDeletionRequest(friendshipId: FriendshipId) {
        mongoTemplate.findAllAndRemove(
            query(where("friendshipId").`is`(friendshipId.toString())), ChatHistory::class.java, "chat"
        )
    }
}
