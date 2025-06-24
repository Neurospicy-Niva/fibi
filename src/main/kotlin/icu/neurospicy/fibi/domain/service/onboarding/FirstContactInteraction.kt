package icu.neurospicy.fibi.domain.service.onboarding

import icu.neurospicy.fibi.domain.model.OutgoingTextMessage
import icu.neurospicy.fibi.domain.model.events.IncomingCuriousMessageReceived
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.incoming.signal.SignalMessageHandler
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class FirstContactInteraction(
    private val friendshipLedger: FriendshipLedger,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    @EventListener
    @Async
    fun introduceTo(event: IncomingCuriousMessageReceived) {
        LOG.info("New user ({}) started chat.", event.friendshipId)
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                _source = this.javaClass,
                event.friendshipId, OutgoingTextMessage(
                    text = """
                        |Hello! I'm Fibi, your personal daily assistant.
                        |Great to have you here! I can help you manage tasks, appointments,
                        |and routines – especially if ADHD or autism make everyday life challenging.
                        |
                        |Before we begin, I need your consent to store and process your data
                        |in line with our privacy policy. You can learn more here: https://neurospicy.icu/tos.
                        |
                        |Are you ready to get started with me?""".trimMargin(), channel = event.message.channel
                ),
                event.message.messageId,
            )
        )
        friendshipLedger.sentTermsOfUseRequest(event.friendshipId)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SignalMessageHandler::class.java)
    }
}
