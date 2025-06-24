package icu.neurospicy.fibi.incoming.signal

import icu.neurospicy.fibi.domain.model.SignalId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.*

@Service
class SignalReactionHandler(
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    fun process(source: String?, reaction: Reaction?) {
        if (source.isNullOrBlank() || reaction == null) {
            LOG.debug("Missing 'source' or 'reaction' in SSE event. Not handling.")
            return
        }

        val userId = SignalId(UUID.fromString(source))
        LOG.info("Processing reaction '{}' from user '{}'", reaction.emoji, source)

        // Publish a reaction event for further processing
        applicationEventPublisher.publishEvent(
            ReactionEvent(
                userId = userId,
                emoji = reaction.emoji,
                targetAuthor = reaction.targetAuthor,
                targetSentTimestamp = reaction.targetSentTimestamp,
                isRemove = reaction.isRemove
            )
        )
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SignalReactionHandler::class.java)
    }
}
