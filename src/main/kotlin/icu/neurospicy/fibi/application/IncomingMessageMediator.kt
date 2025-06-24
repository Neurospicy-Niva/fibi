package icu.neurospicy.fibi.application

import icu.neurospicy.fibi.domain.model.RelationStatus.*
import icu.neurospicy.fibi.domain.model.events.*
import icu.neurospicy.fibi.domain.repository.ChatRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class IncomingMessageMediator(
    private val chatRepository: ChatRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    @Async
    @EventListener
    fun handleIncomingMessage(event: UnidentifiedIncomingMessageReceived) {
        val entry = event.entry
        val incomingMessage = event.incomingMessage
        chatRepository.add(entry.friendshipId, incomingMessage, event.rawEvent)
        LOG.debug(
            "Forwarding message for {} being {} with active activity {}.",
            entry.friendshipId,
            entry.relationStatus,
            entry.activeActivity
        )
        when {
            entry.activeActivity != null -> applicationEventPublisher.publishEvent(
                MessageForActivityReceived(
                    entry.activeActivity,
                    event.entry.friendshipId,
                    incomingMessage
                )
            )

            entry.relationStatus == Curious || entry.relationStatus == FormerFriend -> applicationEventPublisher.publishEvent(
                IncomingCuriousMessageReceived(entry.friendshipId, incomingMessage)
            )

            entry.relationStatus == Acquaintance -> applicationEventPublisher.publishEvent(
                IncomingAcquaintanceMessageReceived(
                    entry.friendshipId,
                    incomingMessage
                )
            )

            entry.relationStatus == Friend -> applicationEventPublisher.publishEvent(
                IncomingFriendMessageReceived(
                    entry.friendshipId,
                    incomingMessage
                )
            )

            else -> LOG.error("No handler for event: $event")
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(IncomingMessageMediator::class.java)
    }
}
