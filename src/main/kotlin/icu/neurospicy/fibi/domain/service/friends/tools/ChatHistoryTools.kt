package icu.neurospicy.fibi.domain.service.friends.tools

import icu.neurospicy.fibi.domain.model.FibiMessage
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.ChatRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import java.time.Instant
import java.time.ZoneOffset.UTC

class ChatHistoryTools(
    private val friendshipLedger: FriendshipLedger,
    private val chatRepository: ChatRepository,
    private val friendshipId: FriendshipId,
) {
    @Tool(description = "Get access to the last messages between you and the user. Get the messages starting from the given date time in ISO 8601 format. If the message is short or context is missing, you must include further messages.")
    fun getChatHistory(startFrom: String): List<LlmMessage> {
        LOG.info("Gathering chat history with friend $friendshipId starting at $startFrom")
        val zoneId = friendshipLedger.findTimezoneBy(friendshipId) ?: UTC
        val startAt = Instant.parse(startFrom)
        return chatRepository.findHistory(friendshipId).timeline.filter {
            when (it) {
                is UserMessage -> it.receivedAt.isAfter(startAt)
                is FibiMessage -> it.sentAt.isAfter(startAt)
            }
        }.map {
            when (it) {
                is UserMessage -> LlmMessage(
                    it.receivedAt.atZone(zoneId).toLocalDateTime().toString(),
                    it.text,
                    "User",
                    it.channel.name
                )

                is FibiMessage -> LlmMessage(
                    it.sentAt.atZone(zoneId).toLocalDateTime().toString(),
                    it.text,
                    "Fibi",
                    it.channel.name
                )
            }
        }.apply { LOG.info("Adding ${count()} messages to message generation for friend $friendshipId") }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ChatHistoryTools::class.java)
    }

    data class LlmMessage(
        val localDateTime: String,
        val message: String,
        val author: String,
        val channel: String,
    )
}

