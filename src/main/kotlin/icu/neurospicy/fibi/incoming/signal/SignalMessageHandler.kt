package icu.neurospicy.fibi.incoming.signal

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.UnidentifiedIncomingMessageReceived
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.outgoing.signal.ConfirmSignalMessageReceived
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class SignalMessageHandler(
    private val friendshipLedger: FriendshipLedger,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    fun process(envelope: EnvelopeData, content: String?, rawEvent: String) {
        if (envelope.source.isNullOrBlank() || content.isNullOrBlank()) {
            LOG.debug("Missing 'source' or 'content' in SSE event. Skipping messsage.")
            return
        }
        LOG.info("Processing message '{}' from user '{}'", content, envelope.source)

        val signalId = SignalId(UUID.fromString(envelope.sourceUuid ?: envelope.source))
        val incomingMessage = UserMessage(
            messageId = SignalMessageId(envelope.timestamp!!),
            text = content,
            channel = Channel.SIGNAL,
            receivedAt = Instant.now()
        )

        val entry =
            Optional.ofNullable(friendshipLedger.findBy(signalId)).orElseGet {
                friendshipLedger.addEntry(
                    signalId = signalId,
                    sourceName = envelope.sourceName,
                    sourceNumber = envelope.sourceNumber
                )
            }
        updateSignalFields(entry, envelope)
        applicationEventPublisher.publishEvent(UnidentifiedIncomingMessageReceived(entry,incomingMessage, rawEvent))
        applicationEventPublisher.publishEvent(ConfirmSignalMessageReceived(entry.friendshipId, SignalMessageId(envelope.timestamp)))
    }

    private fun updateSignalFields(user: LedgerEntry, envelope: EnvelopeData) {
        val updateNumber = envelope.sourceNumber?.isNotBlank() == true && user.signalNumber?.isBlank() == true
        val updateName =
            envelope.sourceName?.isNotBlank() == true && (user.signalName?.isBlank() == true || envelope.sourceName != user.signalName)
        if (updateNumber || updateName) {
            friendshipLedger.updateSignalInfo(
                user.friendshipId,
                if (updateNumber) envelope.sourceNumber else user.signalNumber,
                if (updateName) envelope.sourceName else user.signalName
            )
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SignalReactionHandler::class.java)
    }
}