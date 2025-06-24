package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class SubtaskRegistry(
    contributors: List<SubtaskContributor>,
    private val subtaskHandlers: List<SubtaskHandler>,
) {
    private val byIntent = contributors.groupBy { it.forIntent() }

    fun generateSubtasks(intent: Intent, friendshipId: FriendshipId, message: UserMessage): List<Subtask> =
        runBlocking {
            byIntent[intent].orEmpty().flatMap { it.provideSubtasks(intent, friendshipId, message) }
        }

    @EventListener
    fun handleApplicationStartedEvent(event: ApplicationStartedEvent) {
        LOG.info(
            "Loading subtask contributors for intents with handlers: ${
                byIntent.keys.joinToString {
                    "${it} (${
                        subtaskHandlers.filter { handler ->
                            handler.canHandle(
                                it
                            )
                        }.size
                    } handlers)"
                }
            }")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}